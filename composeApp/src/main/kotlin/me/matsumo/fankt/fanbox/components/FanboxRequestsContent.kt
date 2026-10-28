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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import me.matsumo.fankt.fanbox.FanboxException
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
    "download",
)

@Composable
internal fun FanboxRequestsContent(
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
    val isPost = (
        function
            .returnType
            .jvmErasure == Unit::class
        )

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
                    type = parameter
                        .type
                        .jvmErasure
                        .simpleName ?: "Unknown",
                    onValueChange = { params[key] = it },
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
                        SelectionContainer {
                            Text(
                                modifier = Modifier.padding(16.dp),
                                text = requestResult!!.toString(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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
                        requestResult = executeRequest(
                            function = function,
                            classInstance = classInstance,
                            params = params,
                        )
                    }
                },
            ) {
                Text(
                    text = "Request",
                )
            }
        }
    }
}

@Composable
private fun ParameterItem(
    name: String,
    type: String,
    onValueChange: (String) -> Unit,
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
                style = MaterialTheme
                    .typography
                    .bodyMedium
                    .bold(),
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
                onValueChange.invoke(it)
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onValueChange.invoke(value) },
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
            style = MaterialTheme
                .typography
                .bodyMedium
                .bold(),
        )
    }
}

private suspend fun executeRequest(
    function: KFunction<*>,
    classInstance: Fanbox,
    params: Map<String, String>,
): Any? {
    val typedParams = function
        .parameters
        .mapNotNull { it.name }
        .map { name -> function.parseParameter(name, params) }

    Napier.d { "Call: ${function.name}(${typedParams.joinToString(separator = ", ")})" }

    return try {
        function.callSuspend(classInstance, *typedParams.toTypedArray())
    } catch (failure: FanboxException) {
        Napier.e { "Error calling function: ${function.name}: ${failure.message}" }
        failure
    } catch (failure: Throwable) {
        Napier.e(failure) { "Error calling function: ${function.name}" }
        failure
    }
}

private fun KFunction<*>.parseParameter(name: String, params: Map<String, String>): Any? {
    val type = parameters
        .first { parameter -> parameter.name == name }
        .type
        .jvmErasure
    val value = params[name] ?: getDefaultValue(name) ?: return null

    return when (type) {
        Int::class -> value.toIntOrNull()

        Long::class -> value.toLongOrNull()

        Boolean::class -> value.toBooleanStrictOrNull()

        FanboxPostId::class -> FanboxPostId(value)

        FanboxUserId::class -> FanboxUserId(
            value = value.toLong(),
        )

        FanboxCreatorId::class -> FanboxCreatorId(value)

        FanboxPlanId::class -> FanboxPlanId(value)

        FanboxCommentId::class -> FanboxCommentId(value)

        FanboxNewsLetterId::class -> FanboxNewsLetterId(value)

        else -> value
    }
}

private fun getDefaultValue(paramName: String): String? = when (paramName) {
    "page" -> "1"
    "offset" -> "1"
    "creatorId" -> "rosumerii"
    "userId" -> "67345848"
    "postId" -> "8909488"
    else -> null
}

private fun List<KFunction<*>>.sortedFunctions(): List<KFunction<*>> {
    val actionOrder = listOf("add", "delete", "follow", "unfollow", "like", "get", "search")
    val grouped = groupBy { function ->
        val action = actionOrder.find {
            function
                .name
                .startsWith(it)
        }.orEmpty()
        function
            .name
            .removePrefix(action)
    }

    val sortedGroup = grouped.values
        .sortedBy { group ->
            actionIndex(
                name = group
                    .first()
                    .name,
                actionOrder = actionOrder,
            )
        }
        .map { group -> group.sortedBy { function -> actionIndex(function.name, actionOrder) } }

    return sortedGroup.flatten()
}

private fun actionIndex(name: String, actionOrder: List<String>): Int {
    val action = actionOrder
        .find { name.startsWith(it) }
        .orEmpty()

    return actionOrder
        .indexOf(action)
        .takeIf { it != -1 } ?: Int.MAX_VALUE
}
