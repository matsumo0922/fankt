import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import java.util.zip.ZipFile
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmType
import kotlin.metadata.Visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.visibility

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.metadata.jvm)
        classpath(libs.asm)
    }
}

private val requiredAndroidRuntimeKtorModules = listOf(
    "io.ktor:ktor-client-core",
    "io.ktor:ktor-client-content-negotiation",
    "io.ktor:ktor-serialization-kotlinx-json",
    "io.ktor:ktor-client-logging",
    "io.ktor:ktor-client-okhttp",
)

private fun ResolvedComponentResult.allComponentIdentities(): List<String> {
    val identities = linkedSetOf<String>()
    val pending = ArrayDeque<ResolvedComponentResult>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        if (identities.add(component.id.displayName)) {
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { pending.add(it.selected) }
        }
    }
    return identities.toList()
}

private object AndroidKotlinMetadataInspector {
    fun inspect(
        classFiles: List<File>,
        forbiddenTypeMarkers: Set<String>,
    ): Result {
        val violations = mutableListOf<String>()
        var metadataFiles = 0
        var visibleTypeAliases = 0

        classFiles.forEach { classFile ->
            val header = readMetadataHeader(classFile) ?: return@forEach
            metadataFiles += 1
            val metadata = KotlinClassMetadata.readLenient(header.toMetadata())
            val typeAliases = when (metadata) {
                is KotlinClassMetadata.Class -> metadata.kmClass.typeAliases
                is KotlinClassMetadata.FileFacade -> metadata.kmPackage.typeAliases
                is KotlinClassMetadata.MultiFileClassPart -> metadata.kmPackage.typeAliases
                else -> emptyList()
            }
            typeAliases
                .filter { it.visibility == Visibility.PUBLIC || it.visibility == Visibility.PROTECTED }
                .forEach { typeAlias ->
                    visibleTypeAliases += 1
                    val forbiddenUnderlyingTypes =
                        typeAlias.underlyingType.findForbiddenTypes(forbiddenTypeMarkers)
                    val forbiddenExpandedTypes =
                        typeAlias.expandedType.findForbiddenTypes(forbiddenTypeMarkers)
                    if (forbiddenUnderlyingTypes.isNotEmpty() || forbiddenExpandedTypes.isNotEmpty()) {
                        violations += buildString {
                            append(classFile.name)
                            append(" typealias ")
                            append(typeAlias.name)
                            append(" -> underlying=")
                            append(forbiddenUnderlyingTypes.sorted())
                            append(", expanded=")
                            append(forbiddenExpandedTypes.sorted())
                        }
                    }
                }
        }

        return Result(
            metadataFiles = metadataFiles,
            visibleTypeAliases = visibleTypeAliases,
            violations = violations.sorted(),
        )
    }

    private fun readMetadataHeader(classFile: File): MetadataHeader? {
        var header: MetadataHeader? = null
        ClassReader(classFile.readBytes()).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    if (descriptor != "Lkotlin/Metadata;") return null
                    return MetadataHeader().also { metadataHeader ->
                        header = metadataHeader
                    }.asAnnotationVisitor()
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return header
    }

    private fun MetadataHeader.asAnnotationVisitor(): AnnotationVisitor =
        object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String, value: Any) {
                when (name) {
                    "k" -> kind = value as Int
                    "mv" -> metadataVersion = value as IntArray
                    "xs" -> extraString = value as String
                    "pn" -> packageName = value as String
                    "xi" -> extraInt = value as Int
                }
            }

            override fun visitArray(name: String): AnnotationVisitor =
                object : AnnotationVisitor(Opcodes.ASM9) {
                    private val values = mutableListOf<Any>()

                    override fun visit(elementName: String?, value: Any) {
                        values += value
                    }

                    override fun visitEnd() {
                        when (name) {
                            "mv" -> metadataVersion = values.map { it as Int }.toIntArray()
                            "d1" -> data1 = values.map { it as String }.toTypedArray()
                            "d2" -> data2 = values.map { it as String }.toTypedArray()
                        }
                    }
                }
        }

    private fun KmType.findForbiddenTypes(forbiddenTypeMarkers: Set<String>): Set<String> {
        val result = linkedSetOf<String>()
        val visited = Collections.newSetFromMap(IdentityHashMap<KmType, Boolean>())

        fun visit(type: KmType) {
            if (!visited.add(type)) return
            when (val classifier = type.classifier) {
                is KmClassifier.Class ->
                    classifier.name
                        .takeIf { name -> forbiddenTypeMarkers.any(name::contains) }
                        ?.let(result::add)
                is KmClassifier.TypeAlias ->
                    classifier.name
                        .takeIf { name -> forbiddenTypeMarkers.any(name::contains) }
                        ?.let(result::add)
                is KmClassifier.TypeParameter -> Unit
            }
            type.arguments.forEach { projection -> projection.type?.let(::visit) }
            type.abbreviatedType?.let(::visit)
            type.outerType?.let(::visit)
            type.flexibleTypeUpperBound?.type?.let(::visit)
        }

        visit(this)
        return result
    }

    data class Result(
        val metadataFiles: Int,
        val visibleTypeAliases: Int,
        val violations: List<String>,
    )

    private data class MetadataHeader(
        var kind: Int = 1,
        var metadataVersion: IntArray = intArrayOf(),
        var data1: Array<String> = emptyArray(),
        var data2: Array<String> = emptyArray(),
        var extraString: String = "",
        var packageName: String = "",
        var extraInt: Int = 0,
    ) {
        fun toMetadata(): Metadata = Metadata(
            kind = kind,
            metadataVersion = metadataVersion,
            data1 = data1,
            data2 = data2,
            extraString = extraString,
            packageName = packageName,
            extraInt = extraInt,
        )
    }
}

abstract class VerifyPersistenceBoundaryTask : DefaultTask() {
    @get:Input
    abstract val inspectedConfigurationNames: ListProperty<String>

    @get:Input
    abstract val dependencyIdentities: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val aarFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val configurationNames = inspectedConfigurationNames.get()
        check("androidReleaseCompileClasspath" in configurationNames) {
            "Android release dependency configuration was not inspected"
        }
        check(configurationNames.any { it.matches(Regex("ios.*CompileKlibraries")) }) {
            "iOS dependency configurations were not inspected"
        }
        check("jsCompileClasspath" in configurationNames) {
            "Kotlin/JS dependency configuration was not inspected"
        }

        val forbiddenMarkers = listOf(
            "androidx.room:",
            "androidx.sqlite:",
            "androidx.startup:",
            "project :fankt:fanbox-persistence-room",
        )
        val violations = dependencyIdentities.get().mapNotNull { identity ->
            forbiddenMarkers.firstOrNull(identity::contains)?.let { marker -> "$identity ($marker)" }
        }
        check(violations.isEmpty()) {
            "Core FANBOX dependency boundary violations:\n${violations.joinToString("\n")}"
        }

        val manifests = aarFiles.files
        check(manifests.isNotEmpty()) { "No release AAR was produced for manifest verification" }
        manifests.forEach { aar ->
            val manifest = ZipFile(aar).use { archive ->
                val entry = checkNotNull(archive.getEntry("AndroidManifest.xml")) {
                    "${aar.name}: AndroidManifest.xml is missing from the AAR"
                }
                archive.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            check("androidx.startup.InitializationProvider" !in manifest) {
                "${aar.name}: merged AndroidManifest.xml contains androidx.startup.InitializationProvider"
            }
            check("FanktInitializer" !in manifest) {
                "${aar.name}: merged AndroidManifest.xml contains FanktInitializer"
            }
        }
    }
}

abstract class VerifyKtorBoundaryTask : DefaultTask() {
    @get:Input
    abstract val apiDependencyDeclarations: ListProperty<String>

    @get:Input
    abstract val requiredAndroidRuntimeKtorDependencies: ListProperty<String>

    @get:Input
    abstract val fanboxDependencyDeclarations: ListProperty<String>

    @get:Input
    abstract val infraApiBundleDependencies: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionKotlinFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val abiFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidModuleMetadata: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val publishedModuleMetadata: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val requiredPublishedModuleMetadata: ConfigurableFileCollection

    @get:Input
    abstract val publicationMetadataTaskNames: ListProperty<String>

    @get:Input
    abstract val requiredPublicationMetadataTaskNames: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidAbiFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidClassFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbiddenApiDeclarations = apiDependencyDeclarations.get().filter { "io.ktor:" in it }
        check(forbiddenApiDeclarations.isEmpty()) {
            "Ktor must be an implementation dependency, but API declarations were found:\n" +
                forbiddenApiDeclarations.joinToString("\n")
        }

        val collectedFanboxDependencyDeclarations = fanboxDependencyDeclarations.get()
        check(collectedFanboxDependencyDeclarations.isNotEmpty()) {
            "No FANBOX source set dependency declarations were collected"
        }
        val datetimeDependencyDeclarations = collectedFanboxDependencyDeclarations.filter {
            DATETIME_MODULE in it
        }
        check(datetimeDependencyDeclarations.isEmpty()) {
            "FANBOX source sets still declare kotlinx-datetime dependencies:\n" +
                datetimeDependencyDeclarations.joinToString("\n")
        }
        val collectedInfraApiBundleDependencies = infraApiBundleDependencies.get()
        check(collectedInfraApiBundleDependencies.isNotEmpty()) {
            "The infra-api bundle resolved to no dependencies"
        }
        val datetimeInfraApiDependencies = collectedInfraApiBundleDependencies.filter {
            DATETIME_MODULE in it
        }
        check(datetimeInfraApiDependencies.isEmpty()) {
            "The infra-api bundle still contains kotlinx-datetime dependencies:\n" +
                datetimeInfraApiDependencies.joinToString("\n")
        }

        // This literal scan is an early supplemental signal; dependency, ABI, and metadata gates
        // below enforce the complete published boundary.
        val productionKotlinSourceFiles = productionKotlinFiles.files.filter(File::isFile)
        check(productionKotlinSourceFiles.isNotEmpty()) {
            "No production Kotlin sources were scanned"
        }
        val deprecatedDatetimeSourceViolations = productionKotlinSourceFiles
            .filter { source -> source.readText().containsDeprecatedDatetimeType() }
        check(deprecatedDatetimeSourceViolations.isEmpty()) {
            "Deprecated kotlinx-datetime Instant or Clock remains in production source:\n" +
                deprecatedDatetimeSourceViolations.joinToString("\n") {
                    it.relativeTo(project.projectDir).path
                }
        }

        val checkedAbiFiles = abiFiles.files.filter { it.isFile }
        check(checkedAbiFiles.isNotEmpty()) {
            "No checked-in fanbox ABI files were found; run :fankt:fanbox:updateLegacyAbi"
        }
        val leakingAbiFiles = checkedAbiFiles.filter { abi -> abi.readText().containsKtorType() }
        check(leakingAbiFiles.isEmpty()) {
            "Ktor types leaked into the public fanbox ABI:\n" +
                leakingAbiFiles.joinToString("\n") { it.relativeTo(project.projectDir).path }
        }
        val deprecatedDatetimeAbiFiles = checkedAbiFiles.filter { abi ->
            abi.readText().containsDeprecatedDatetimeType()
        }
        check(deprecatedDatetimeAbiFiles.isEmpty()) {
            "Deprecated kotlinx-datetime Instant or Clock leaked into the public fanbox ABI:\n" +
                deprecatedDatetimeAbiFiles.joinToString("\n") {
                    it.relativeTo(project.projectDir).path
                }
        }

        val genericSignatureViolations = inspectAndroidGenericSignatures(
            abiText = androidAbiFile.get().asFile.readText(),
            classFiles = androidClassFiles.files.filter { it.isFile },
        )
        check(genericSignatureViolations.isEmpty()) {
            "Ktor types leaked into Android public/protected generic signatures:\n" +
                genericSignatureViolations.joinToString("\n")
        }

        val typeAliasClassFiles = androidClassFiles.files.filter { it.isFile }
        val typeAliasInspection = AndroidKotlinMetadataInspector.inspect(
            classFiles = typeAliasClassFiles,
            forbiddenTypeMarkers = KTOR_TYPE_MARKERS,
        )
        check(typeAliasInspection.metadataFiles > 0) {
            "No Android Kotlin metadata was inspected"
        }
        check(typeAliasInspection.violations.isEmpty()) {
            "Ktor types leaked through Android public/protected type aliases:\n" +
                typeAliasInspection.violations.joinToString("\n")
        }
        val datetimeTypeAliasInspection = AndroidKotlinMetadataInspector.inspect(
            classFiles = typeAliasClassFiles,
            forbiddenTypeMarkers = DEPRECATED_DATETIME_TYPES.toSet(),
        )
        check(datetimeTypeAliasInspection.violations.isEmpty()) {
            "Deprecated kotlinx-datetime types leaked through Android public/protected type aliases:\n" +
                datetimeTypeAliasInspection.violations.joinToString("\n")
        }

        val metadataFiles = androidModuleMetadata.files.filter { it.isFile }
        check(metadataFiles.isNotEmpty()) { "No Android Gradle module metadata was generated" }
        val apiViolations = metadataFiles.flatMap { metadataFile ->
            val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
            val variants = metadata["variants"] as? List<*> ?: emptyList<Any>()
            variants.flatMap variantLoop@{ variantValue ->
                val variant = variantValue as? Map<*, *> ?: return@variantLoop emptyList()
                val attributes = variant["attributes"] as? Map<*, *> ?: emptyMap<Any, Any>()
                if (attributes["org.gradle.usage"] != "java-api") return@variantLoop emptyList()
                val variantName = variant["name"].toString()
                val dependencies = variant["dependencies"] as? List<*> ?: emptyList<Any>()
                dependencies.mapNotNull dependencyLoop@{ dependencyValue ->
                    val dependency = dependencyValue as? Map<*, *> ?: return@dependencyLoop null
                    if (dependency["group"] == "io.ktor") {
                        "${metadataFile.name}: $variantName -> ${dependency["group"]}:${dependency["module"]}"
                    } else {
                        null
                    }
                }
            }
        }
        check(apiViolations.isEmpty()) {
            "Ktor dependencies leaked into Android API publication metadata:\n" +
                apiViolations.joinToString("\n")
        }

        val publishedRuntimeKtorDependencies = metadataFiles.flatMap { metadataFile ->
            val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
            val variants = metadata["variants"] as? List<*> ?: emptyList<Any>()
            variants.flatMap variantLoop@{ variantValue ->
                val variant = variantValue as? Map<*, *> ?: return@variantLoop emptyList()
                val attributes = variant["attributes"] as? Map<*, *> ?: emptyMap<Any, Any>()
                if (attributes["org.gradle.usage"] != "java-runtime") return@variantLoop emptyList()
                val dependencies = variant["dependencies"] as? List<*> ?: emptyList<Any>()
                dependencies.mapNotNull dependencyLoop@{ dependencyValue ->
                    val dependency = dependencyValue as? Map<*, *> ?: return@dependencyLoop null
                    if (dependency["group"] == "io.ktor") {
                        "${dependency["group"]}:${dependency["module"]}"
                    } else {
                        null
                    }
                }
            }
        }.toSet()
        val missingRuntimeDependencies =
            requiredAndroidRuntimeKtorDependencies.get().toSet() - publishedRuntimeKtorDependencies
        check(missingRuntimeDependencies.isEmpty()) {
            "Android runtime publication metadata is missing required Ktor dependencies:\n" +
                missingRuntimeDependencies.joinToString("\n")
        }

        val publishedMetadataFiles = publishedModuleMetadata.files.filter { it.isFile }
        val missingPublicationMetadataTasks =
            requiredPublicationMetadataTaskNames.get().toSet() -
                publicationMetadataTaskNames.get().toSet()
        check(missingPublicationMetadataTasks.isEmpty()) {
            "FANBOX publication metadata tasks were not inspected:\n" +
                missingPublicationMetadataTasks.joinToString("\n")
        }
        val requiredPublishedMetadataOutputs = requiredPublishedModuleMetadata.files
        check(
            requiredPublishedMetadataOutputs.size ==
                requiredPublicationMetadataTaskNames.get().size,
        ) {
            "Expected one publication metadata output per required task, but found " +
                "${requiredPublishedMetadataOutputs.size} outputs for " +
                "${requiredPublicationMetadataTaskNames.get().size} tasks"
        }
        val missingPublishedMetadataOutputs = requiredPublishedMetadataOutputs.filterNot(File::isFile)
        check(missingPublishedMetadataOutputs.isEmpty()) {
            "Required FANBOX publication metadata was not generated:\n" +
                missingPublishedMetadataOutputs.joinToString("\n") { it.path }
        }
        check(publishedMetadataFiles.isNotEmpty()) {
            "No Android or Kotlin Multiplatform publication metadata was generated"
        }
        val datetimeMetadataViolations = publishedMetadataFiles.flatMap { metadataFile ->
            val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
            val variants = metadata["variants"] as? List<*> ?: emptyList<Any>()
            variants.flatMap variantLoop@{ variantValue ->
                val variant = variantValue as? Map<*, *> ?: return@variantLoop emptyList()
                val variantName = variant["name"].toString()
                val dependencies = variant["dependencies"] as? List<*> ?: emptyList<Any>()
                dependencies.mapNotNull dependencyLoop@{ dependencyValue ->
                    val dependency = dependencyValue as? Map<*, *> ?: return@dependencyLoop null
                    if (
                        dependency["group"] == "org.jetbrains.kotlinx" &&
                        dependency["module"].toString().startsWith("kotlinx-datetime")
                    ) {
                        "${metadataFile.name}: $variantName -> " +
                            "${dependency["group"]}:${dependency["module"]}"
                    } else {
                        null
                    }
                }
            }
        }
        check(datetimeMetadataViolations.isEmpty()) {
            "kotlinx-datetime leaked into published FANBOX metadata:\n" +
                datetimeMetadataViolations.joinToString("\n")
        }
    }

    private fun inspectAndroidGenericSignatures(
        abiText: String,
        classFiles: List<File>,
    ): List<String> {
        val visibleApi = parseVisibleAndroidApi(abiText)
        check(visibleApi.isNotEmpty()) { "Android ABI baseline contains no visible classes" }
        check(classFiles.isNotEmpty()) { "No compiled Android release classes were found" }

        val violations = mutableListOf<String>()
        val matchedClasses = linkedSetOf<String>()
        var matchedMembers = 0
        var genericSignatures = 0
        classFiles.forEach { classFile ->
            ClassReader(classFile.readBytes()).accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    private var visibleClass: VisibleAndroidClass? = null
                    private var className: String = classFile.name

                    override fun visit(
                        version: Int,
                        access: Int,
                        name: String,
                        signature: String?,
                        superName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        className = name
                        visibleClass = visibleApi[name]
                        if (visibleClass != null) {
                            matchedClasses += name
                            if (signature != null) genericSignatures += 1
                            recordKtorSignature("$name class", signature, violations)
                        }
                    }

                    override fun visitField(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        value: Any?,
                    ): FieldVisitor? {
                        val visible = visibleClass ?: return null
                        if (VisibleAndroidMember(name, descriptor) in visible.fields) {
                            matchedMembers += 1
                            if (signature != null) genericSignatures += 1
                            recordKtorSignature("$className.$name", signature, violations)
                        }
                        return null
                    }

                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor? {
                        val visible = visibleClass ?: return null
                        if (VisibleAndroidMember(name, descriptor) in visible.methods) {
                            matchedMembers += 1
                            if (signature != null) genericSignatures += 1
                            recordKtorSignature("$className.$name$descriptor", signature, violations)
                        }
                        return null
                    }
                },
                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
        }
        check(matchedClasses == visibleApi.keys) {
            "Compiled Android classes did not cover the checked ABI:\n" +
                (visibleApi.keys - matchedClasses).joinToString("\n")
        }
        val expectedMembers = visibleApi.values.sumOf { visible ->
            visible.fields.size + visible.methods.size
        }
        check(matchedMembers == expectedMembers) {
            "Compiled Android members did not cover the checked ABI: " +
                "matched $matchedMembers of $expectedMembers"
        }
        check(genericSignatures > 0) {
            "No Android generic Signature attributes were inspected"
        }
        return violations.sorted()
    }

    private fun parseVisibleAndroidApi(abiText: String): Map<String, VisibleAndroidClass> {
        val classPattern = Regex("^(?:public|protected)\\s+.*\\bclass\\s+([^\\s:]+)")
        val methodPattern = Regex("^\\t(?:public|protected)\\s+.*\\bfun\\s+(\\S+)\\s+(\\([^)]*\\)\\S+)$")
        val fieldPattern = Regex("^\\t(?:public|protected)\\s+.*\\bfield\\s+(\\S+)\\s+(\\S+)$")
        val classes = linkedMapOf<String, VisibleAndroidClass>()
        var current: VisibleAndroidClass? = null

        abiText.lineSequence().forEach { line ->
            classPattern.find(line)?.let { match ->
                current = VisibleAndroidClass()
                classes[match.groupValues[1]] = checkNotNull(current)
                return@forEach
            }
            methodPattern.find(line)?.let { match ->
                current?.methods?.add(VisibleAndroidMember(match.groupValues[1], match.groupValues[2]))
                return@forEach
            }
            fieldPattern.find(line)?.let { match ->
                current?.fields?.add(VisibleAndroidMember(match.groupValues[1], match.groupValues[2]))
            }
        }
        return classes
    }

    private fun recordKtorSignature(
        declaration: String,
        signature: String?,
        violations: MutableList<String>,
    ) {
        if (signature?.containsKtorType() == true) {
            violations += "$declaration -> $signature"
        }
    }

    private fun String.containsKtorType(): Boolean =
        KTOR_DOTTED_PACKAGE in this || KTOR_INTERNAL_PACKAGE in this

    private fun String.containsDeprecatedDatetimeType(): Boolean =
        DEPRECATED_DATETIME_TYPES.any(this::contains)

    private data class VisibleAndroidClass(
        val methods: MutableSet<VisibleAndroidMember> = linkedSetOf(),
        val fields: MutableSet<VisibleAndroidMember> = linkedSetOf(),
    )

    private data class VisibleAndroidMember(
        val name: String,
        val descriptor: String,
    )

    private companion object {
        const val KTOR_DOTTED_PACKAGE = "io.ktor"
        const val KTOR_INTERNAL_PACKAGE = "io/ktor"
        const val DATETIME_MODULE = "org.jetbrains.kotlinx:kotlinx-datetime"
        val KTOR_TYPE_MARKERS = setOf(KTOR_DOTTED_PACKAGE, KTOR_INTERNAL_PACKAGE)
        val DEPRECATED_DATETIME_TYPES = listOf(
            "kotlinx.datetime.Instant",
            "kotlinx.datetime.Clock",
            "kotlinx/datetime/Instant",
            "kotlinx/datetime/Clock",
        )
    }
}

abstract class VerifyPortableImportBoundaryTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val portableSourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val forbiddenImportRoots: ListProperty<String>

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val sourceFiles = portableSourceFiles.files.filter(File::isFile)
        check(sourceFiles.isNotEmpty()) { "No portable FANBOX core sources were scanned" }
        val roots = forbiddenImportRoots.get()
        val importPattern = Regex("^\\s*import\\s+(\\S+)")
        val baseDirectory = projectDirectory.get().asFile
        val violations = sourceFiles.flatMap { source ->
            source.readLines().mapNotNull { line ->
                val importedName = importPattern.find(line)?.groupValues?.get(1)
                importedName
                    ?.takeIf { name -> roots.any { root -> name == root || name.startsWith("$root.") } }
                    ?.let { name -> "${source.relativeTo(baseDirectory).path}: import $name" }
            }
        }.sorted()
        check(violations.isEmpty()) {
            "Forbidden imports in the portable FANBOX core:\n${violations.joinToString("\n")}"
        }
    }
}

abstract class VerifyKtorTypeAliasFixtureTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixtureClassFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val classFiles = fixtureClassFiles.files.filter { it.isFile }
        check(classFiles.isNotEmpty()) { "No Android Ktor typealias regression fixture was compiled" }
        val ktorInspection = AndroidKotlinMetadataInspector.inspect(
            classFiles = classFiles,
            forbiddenTypeMarkers = setOf("io.ktor", "io/ktor"),
        )
        check(ktorInspection.metadataFiles > 0) {
            "Android Ktor typealias regression fixture contains no Kotlin metadata"
        }
        check(ktorInspection.visibleTypeAliases > 0) {
            "Android Ktor typealias regression fixture contains no public/protected typealias metadata"
        }
        check(
            ktorInspection.violations.any { violation ->
                "AndroidKtorClientAlias" in violation && "io/ktor/client/HttpClient" in violation
            },
        ) {
            "The Android Ktor typealias regression fixture did not trigger the production metadata gate:\n" +
                ktorInspection.violations.joinToString("\n")
        }
        val instantInspection = AndroidKotlinMetadataInspector.inspect(
            classFiles = classFiles,
            forbiddenTypeMarkers = setOf("kotlin/time/Instant"),
        )
        check(
            instantInspection.violations.any { violation ->
                "AndroidGenericMarkerProxyAlias" in violation && "kotlin/time/Instant" in violation
            },
        ) {
            "The Android Instant typealias regression fixture did not trigger the generic metadata gate:\n" +
                instantInspection.violations.joinToString("\n")
        }
    }
}

plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.android.common")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.kmp.js")
    id("matsumo.primitive.detekt")
    id("matsumo.primitive.maven.publish")
    alias(libs.plugins.zipline)
}

val persistenceBoundaryConfigurations = configurations.matching { configuration ->
    if (!configuration.isCanBeResolved) {
        false
    } else {
        configuration.name == "androidReleaseCompileClasspath" ||
            configuration.name == "commonMainResolvableDependenciesMetadata" ||
            configuration.name == "jsCompileClasspath" ||
            configuration.name.matches(Regex("ios.*CompileKlibraries"))
    }
}

tasks.register<VerifyPersistenceBoundaryTask>("verifyPersistenceBoundary") {
    group = "verification"
    description = "Verifies that the core FANBOX artifact has no optional persistence dependencies"
    dependsOn("bundleReleaseAar")

    inspectedConfigurationNames.set(persistenceBoundaryConfigurations.names.sorted())
    persistenceBoundaryConfigurations.forEach { configuration ->
        val configurationName = configuration.name
        dependencyIdentities.addAll(
            configuration.incoming.resolutionResult.rootComponent.map { rootComponent ->
                rootComponent.allComponentIdentities().map { identity ->
                    "$configurationName: ${identity.lowercase()}"
                }
            },
        )
    }
    aarFiles.from(
        layout.buildDirectory.dir("outputs/aar").map { outputDirectory ->
            outputDirectory.asFileTree.matching { include("*-release.aar") }
        },
    )
}

android {
    namespace = "me.matsumo.fankt.fanbox"
}

zipline {
    mainFunction.set("me.matsumo.fankt.fanbox.guest.launchZipline")
}

// The guest target exists to build the bundle, not to be consumed as a dependency. Kotlin
// Multiplatform has no API to keep a target out of publishing, so its tasks are disabled instead.
tasks.matching { task -> task.name.contains("GuestPublication") }.configureEach {
    enabled = false
}

// Two Kotlin/JS targets share a platform type, so consumers need an attribute to tell their
// variants apart.
val fanboxJsTargetAttribute: Attribute<String> = Attribute.of("me.matsumo.fankt.jsTarget", String::class.java)

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {}

    // The guest bundle needs an executable binary with an entry point, and the existing `js` target
    // publishes a library. Zipline's serve task name omits the binary kind, so declaring both on one
    // target registers it twice and fails configuration. A second target keeps them apart.
    js("guest", org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType.IR) {
        nodejs()
        binaries.executable()
        attributes.attribute(fanboxJsTargetAttribute, "guest")
    }

    targets.named("js") {
        attributes.attribute(fanboxJsTargetAttribute, "library")
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val clientMain by creating { dependsOn(commonMain) }
        val clientTest by creating { dependsOn(commonTest) }
        val androidMain by getting { dependsOn(clientMain) }
        val androidUnitTest by getting { dependsOn(clientTest) }
        val iosMain by getting { dependsOn(clientMain) }
        getByName("iosTest") { dependsOn(clientTest) }

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)

            // The bridge service interface is shared by the host and the guest, so its Zipline
            // dependency reaches every published target.
            implementation(libs.zipline)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        clientMain.dependencies {
            implementation(libs.napier)
            implementation(libs.bundles.ktor)
            implementation(libs.ksoup)
        }

        clientTest.dependencies {
            implementation(libs.ktor.mock)
        }

        androidMain.dependencies {
            implementation(libs.ktor.okhttp)
        }

        androidUnitTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }

        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
    }
}

val sourceSetDependencyConfigurations = configurations.matching { configuration ->
    configuration.name.matches(Regex(".*Main(Api|Implementation|CompileOnly|RuntimeOnly)"))
}
val sourceSetApiConfigurations = sourceSetDependencyConfigurations.matching { configuration ->
    configuration.name.endsWith("MainApi")
}
val androidMetadataTasks = tasks.withType<GenerateModuleMetadata>().matching {
    it.name == "generateMetadataFileForAndroidReleasePublication"
}
val publicationMetadataTasks = tasks.withType<GenerateModuleMetadata>()
val requiredPublicationMetadataTasks = buildList {
    add("generateMetadataFileForAndroidReleasePublication")
    add("generateMetadataFileForKotlinMultiplatformPublication")
    add("generateMetadataFileForJsPublication")
    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        add("generateMetadataFileForIosArm64Publication")
        add("generateMetadataFileForIosSimulatorArm64Publication")
        add("generateMetadataFileForIosX64Publication")
    }
}
val requiredPublicationMetadataTaskOutputs = publicationMetadataTasks.matching { task ->
    task.name in requiredPublicationMetadataTasks
}

val verifyKtorTypeAliasFixture = tasks.register<VerifyKtorTypeAliasFixtureTask>(
    "verifyKtorTypeAliasFixture",
) {
    group = "verification"
    description = "Proves that Android public Ktor type aliases fail the Kotlin metadata boundary"
    dependsOn("compileDebugUnitTestKotlinAndroid")
    fixtureClassFiles.from(
        layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest").map { outputDirectory ->
            outputDirectory.asFileTree.matching {
                include("**/KtorBoundaryTypeAliasFixtureKt.class")
            }
        },
    )
}

val verifyKtorBoundary = tasks.register<VerifyKtorBoundaryTask>("verifyKtorBoundary") {
    group = "verification"
    description = "Verifies that Ktor remains outside the public FANBOX API boundary"
    dependsOn(
        "checkKotlinAbi",
        "compileReleaseKotlinAndroid",
        publicationMetadataTasks,
        verifyKtorTypeAliasFixture,
    )

    productionKotlinFiles.from(
        layout.projectDirectory.dir("src").asFileTree.matching {
            include("*Main/kotlin/**/*.kt")
        },
    )
    infraApiBundleDependencies.set(
        provider {
            libs.bundles.infra.api.get().map { dependency ->
                "${dependency.module.group}:${dependency.module.name}"
            }
        },
    )
    abiFiles.from(layout.projectDirectory.dir("api").asFileTree.matching { include("**/*.api") })
    androidAbiFile.set(layout.projectDirectory.file("api/android/fanbox.api"))
    androidClassFiles.from(
        layout.buildDirectory.dir("tmp/kotlin-classes/release").map { outputDirectory ->
            outputDirectory.asFileTree.matching { include("**/*.class") }
        },
    )
    androidModuleMetadata.from(androidMetadataTasks)
    publishedModuleMetadata.from(publicationMetadataTasks)
    requiredPublishedModuleMetadata.from(requiredPublicationMetadataTaskOutputs)
    publicationMetadataTaskNames.set(publicationMetadataTasks.names.sorted())
    requiredPublicationMetadataTaskNames.set(requiredPublicationMetadataTasks.sorted())
    requiredAndroidRuntimeKtorDependencies.set(requiredAndroidRuntimeKtorModules)
}

sourceSetApiConfigurations.configureEach {
    val apiConfiguration = this
    verifyKtorBoundary.configure {
        apiDependencyDeclarations.addAll(
            provider {
                apiConfiguration.dependencies.map { dependency ->
                    "${apiConfiguration.name}: ${dependency.group}:${dependency.name}"
                }
            },
        )
    }
}

sourceSetDependencyConfigurations.configureEach {
    val dependencyConfiguration = this
    verifyKtorBoundary.configure {
        fanboxDependencyDeclarations.addAll(
            provider {
                dependencyConfiguration.dependencies.map { dependency ->
                    "${dependencyConfiguration.name}: ${dependency.group}:${dependency.name}"
                }
            },
        )
    }
}

val verifyPortableImportBoundary = tasks.register<VerifyPortableImportBoundaryTask>(
    "verifyPortableImportBoundary",
) {
    group = "verification"
    description = "Rejects Ktor, Room, Napier, Ksoup, and JVM-only imports from the portable FANBOX core"
    projectDirectory.set(layout.projectDirectory)
    portableSourceFiles.from(
        layout.projectDirectory.dir("src").asFileTree.matching {
            include("commonMain/kotlin/me/matsumo/fankt/fanbox/**/*.kt")
        },
    )
    forbiddenImportRoots.set(
        listOf(
            "io.ktor",
            "androidx.room",
            "io.github.aakira.napier",
            "com.fleeksoft.ksoup",
            "java",
            "javax",
        ),
    )
}

tasks.named("check") {
    dependsOn("verifyKtorBoundary", verifyPortableImportBoundary)
}
