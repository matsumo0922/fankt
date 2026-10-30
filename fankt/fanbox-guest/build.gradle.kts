import app.cash.zipline.loader.SignatureAlgorithmId

plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.detekt")
    alias(libs.plugins.zipline)
}

zipline {
    mainFunction.set("me.matsumo.fankt.fanbox.guest.launchZipline")
    // The delivery workflow passes the commit it builds, which consumers receive when the guest loads.
    version.set(providers.gradleProperty("fanboxZiplineVersion"))

    // The delivery workflow passes the key from a repository secret. Local builds and pull request
    // CI have no secret, and they build an unsigned manifest rather than failing: the loader
    // requires a signature, so an unsigned bundle cannot be executed by a consumer even if it
    // escapes. The workflow separately refuses to publish a manifest that carries no signature.
    // The key name switches to the recovery key when the primary key is lost. See the README.
    val signingPrivateKeyHex = providers.gradleProperty("fanboxZiplineSigningPrivateKeyHex")
    val signingKeyName = providers.gradleProperty("fanboxZiplineSigningKeyName").orElse("fanboxGuest")
    if (signingPrivateKeyHex.isPresent) {
        signingKeys.create(signingKeyName.get()) {
            algorithmId.set(SignatureAlgorithmId.Ed25519)
            privateKeyHex.set(signingPrivateKeyHex)
        }
    }
}

kotlin {
    // The guest bundle needs an executable binary with an entry point.
    js {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        all {
            languageSettings.optIn("me.matsumo.fankt.fanbox.InternalFanboxApi")
        }

        commonMain.dependencies {
            implementation(project(":fankt:fanbox-core"))
        }
    }
}
