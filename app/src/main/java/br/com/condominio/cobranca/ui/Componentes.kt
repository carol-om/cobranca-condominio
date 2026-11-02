@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.condominio.cobranca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Estrutura padrão de toda tela: barra colorida com seta de voltar, botão flutuante e barra de ação fixa embaixo. */
@Composable
fun Pagina(
    titulo: String,
    voltar: () -> Unit,
    botaoFlutuante: @Composable () -> Unit = {},
    barraInferior: @Composable () -> Unit = {},
    conteudo: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(titulo, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = voltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = barraInferior,
        floatingActionButton = botaoFlutuante,
        content = conteudo,
    )
}

/** Faixa fixa no rodapé da tela para o botão principal (Salvar, Criar, Fechar...). */
@Composable
fun BarraAcao(conteudo: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { conteudo() }
    }
}

/** Cartão branco com cantos arredondados. Se receber aoClicar, vira um cartão tocável. */
@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    aoClicar: (() -> Unit)? = null,
    cor: Color? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val cores = CardDefaults.cardColors(containerColor = cor ?: MaterialTheme.colorScheme.surface)
    val elevacao = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (aoClicar != null) {
        Card(
            onClick = aoClicar,
            modifier = modifier.fillMaxWidth(),
            colors = cores,
            elevation = elevacao,
            content = conteudo,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = cores,
            elevation = elevacao,
            content = conteudo,
        )
    }
}

@Composable
fun BotaoPrincipal(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    habilitado: Boolean = true,
    icone: ImageVector? = null,
) {
    Button(
        onClick = aoClicar,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = habilitado,
        shape = RoundedCornerShape(14.dp),
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BotaoSecundario(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    habilitado: Boolean = true,
    icone: ImageVector? = null,
) {
    OutlinedButton(
        onClick = aoClicar,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = habilitado,
        shape = RoundedCornerShape(14.dp),
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Título de grupo dentro de uma tela, com explicação curta opcional. */
@Composable
fun Secao(titulo: String, descricao: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (descricao != null) {
            Text(descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IconeRedondo(
    icone: ImageVector,
    modifier: Modifier = Modifier,
    tamanho: Dp = 44.dp,
    fundo: Color = MaterialTheme.colorScheme.primaryContainer,
    cor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier.size(tamanho).background(fundo, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = cor)
    }
}

/** Mensagem amigável quando uma lista ainda não tem nada. */
@Composable
fun EstadoVazio(icone: ImageVector, titulo: String, texto: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconeRedondo(icone, tamanho = 64.dp)
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Linha "descrição ........ valor". Com destaque, vira o total em cor forte. */
@Composable
fun LinhaValor(rotulo: String, valor: String, destaque: Boolean = false) {
    val estilo = if (destaque) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, style = estilo, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(
            valor,
            style = estilo,
            color = if (destaque) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Item de menu da tela inicial: ícone, título, explicação e seta (ou "feito"). */
@Composable
fun LinhaMenu(
    icone: ImageVector,
    titulo: String,
    descricao: String,
    aoClicar: () -> Unit,
    concluido: Boolean = false,
) {
    Cartao(aoClicar = aoClicar) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconeRedondo(icone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(
                    descricao,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (concluido) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Concluído", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Botões de editar e excluir com ícone. */
@Composable
fun Acoes(editar: () -> Unit, excluir: () -> Unit) {
    Row {
        IconButton(onClick = editar) {
            Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = excluir) {
            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** Caixa de confirmação. Com perigo = true, o botão de confirmar fica vermelho. */
@Composable
fun Confirmar(
    titulo: String,
    mensagem: String,
    textoConfirmar: String,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
    perigo: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(mensagem) },
        confirmButton = {
            TextButton(onClick = aoConfirmar) {
                Text(
                    textoConfirmar,
                    fontWeight = FontWeight.SemiBold,
                    color = if (perigo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}

/** Campo de texto com cantos arredondados e mensagem de erro padrão. */
@Composable
fun Campo(
    valor: String,
    mudar: (String) -> Unit,
    rotulo: String,
    erro: Boolean = false,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    linhas: Int = 1,
    dica: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = valor,
        onValueChange = mudar,
        label = { Text(rotulo) },
        placeholder = if (dica != null) ({ Text(dica) }) else null,
        isError = erro,
        supportingText = if (erro) ({ Text("Preencha este campo corretamente") }) else null,
        keyboardOptions = teclado,
        minLines = linhas,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}
