package me.matsumo.fankt.fanbox.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import me.matsumo.fankt.fanbox.Fanbox
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxNewsLetterId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPlanId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.theme.bold
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure

private val excludeMethod = setOf(
    "setFanboxSessionId",
    "updateCsrfToken",
    "downloadPostImage",
    "downloadPostThumbnailImage",
)

@Composable
fun FanboxRequestsContent(
    classInstance: Fanbox,
    modifier: Modifier = Modifier,
) {
    val functions = remember {
        classInstance::class.memberFunctions
            .filter { it.visibility == KVisibility.PUBLIC && it.isSuspend && it.name !in excludeMethod }
            .sortedFunctions()
    }

    LazyColumn(
        modifier = modifier,
    ) {
        items(functions.size) { index ->
            RequestItem(
                classInstance = classInstance,
                function = functions[index],
            )
        }
    }
}

@Composable
private fun RequestItem(
    classInstance: Fanbox,
    function: KFunction<*>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val params = rememberSaveable { mutableMapOf<String, String>() }

    var requestResult by remember { mutableStateOf<Any?>(null) }
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val isPost = (function.returnType.jvmErasure == Unit::class)

    suspend fun request() {
        val typedParams = function.parameters
            .mapNotNull { it.name }
            .map {
                val type = function.parameters.first { param -> param.name == it }.type.jvmErasure
                val param = params[it] ?: getDefaultValue(it) ?: return@map null

                when (type) {
                    Int::class -> param.toIntOrNull()
                    Long::class -> param.toLongOrNull()
                    Boolean::class -> param.toBooleanStrictOrNull()
                    FanboxPostId::class -> FanboxPostId(param)
                    FanboxUserId::class -> FanboxUserId(param.toLong())
                    FanboxCreatorId::class -> FanboxCreatorId(param)
                    FanboxPlanId::class -> FanboxPlanId(param)
                    FanboxCommentId::class -> FanboxCommentId(param)
                    FanboxNewsLetterId::class -> FanboxNewsLetterId(param)
                    else -> param
                }
            }

        Napier.d { "Call: ${function.name}(${typedParams.joinToString(separator = ", ")})" }

        requestResult = try {
            function.callSuspend(classInstance, *typedParams.toTypedArray())
        } catch (e: Throwable) {
            Napier.d { e.toString() }
            e
        }
    }

    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .fillMaxWidth()
                .padding(16.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RequestTypeBadge(isPost)

            Text(
                modifier = Modifier.weight(1f),
                text = function.name,
                style = MaterialTheme.typography.titleMedium,
            )

            IconButton(
                onClick = { isExpanded = !isExpanded },
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
        }

        if (isExpanded) {
            for (parameter in function.valueParameters) {
                val key = parameter.name ?: "Unknown"

                ParameterItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    name = key,
                    type = parameter.type.jvmErasure.simpleName ?: "Unknown",
                    onValueChanged = { params[key] = it },
                )
            }

            if (requestResult != null) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        if (requestResult !is Throwable) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = requestResult!!.toString(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Button(
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth(),
                onClick = {
                    scope.launch {
                        requestResult = null
                        request()
                    }
                },
            ) {
                Text("Request")
            }
        }
    }
}

@Composable
private fun ParameterItem(
    name: String,
    type: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (value, setValue) = remember { mutableStateOf("") }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.bold(),
            )

            Text(
                text = type + "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = {
                setValue.invoke(it)
                onValueChanged.invoke(it)
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onValueChanged.invoke(value) },
            ),
            singleLine = true,
            placeholder = getDefaultValue(name)?.let { { Text(it) } },
        )
    }
}

@Composable
private fun RequestTypeBadge(
    isPost: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor: Color
    val text: String

    if (isPost) {
        containerColor = Color.Green
        text = "POST"
    } else {
        containerColor = Color.Blue
        text = "GET"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
    ) {
        Text(
            modifier = Modifier.padding(8.dp, 4.dp),
            text = text,
            style = MaterialTheme.typography.bodyMedium.bold(),
        )
    }
}

private fun getDefaultValue(paramName: String): String? {
    return when (paramName) {
        "page" -> "1"
        "offset" -> "1"
        "creatorId" -> "rosumerii"
        "userId" -> "67345848"
        "postId" -> "8909488"
        "rootCommentId" -> "0"
        "parentCommentId" -> "0"
        else -> null
    }
}

private fun List<KFunction<*>>.sortedFunctions(): List<KFunction<*>> {
    val actionOrder = listOf("add", "delete", "follow", "unfollow", "like", "get", "search")
    val grouped = groupBy { function ->
        val action = actionOrder.find { function.name.startsWith(it) } ?: ""
        function.name.removePrefix(action)
    }

    val sortedGroup = grouped.values
        .sortedBy { group ->
            val name = group.first().name
            val action = actionOrder.find { name.startsWith(it) } ?: ""

            actionOrder.indexOf(action).takeIf { it != -1 } ?: Int.MAX_VALUE
        }
        .map { group ->
            group.sortedBy { function ->
                val name = function.name
                val action = actionOrder.find { name.startsWith(it) } ?: ""

                actionOrder.indexOf(action).takeIf { it != -1 } ?: Int.MAX_VALUE
            }
        }

    return sortedGroup.flatten()
}
