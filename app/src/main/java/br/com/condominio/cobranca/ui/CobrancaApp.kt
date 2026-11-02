@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.condominio.cobranca.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.condominio.cobranca.calculo.CalculadoraDeRegras
import br.com.condominio.cobranca.calculo.UnidadeDaRegra
import br.com.condominio.cobranca.dados.*
import br.com.condominio.cobranca.imagem.GeradorDeImagem
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class CobrancaViewModel(private val repositorio: CobrancaRepositorio) : ViewModel() {
    val condominio = repositorio.observarCondominio().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val unidades = repositorio.observarUnidades().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val despesas = repositorio.observarDespesas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rascunho = repositorio.observarRascunho().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val cobrancasFechadas = repositorio.observarFechadas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun salvarCondominio(nome: String, sindica: String, pix: String, instrucoes: String) = viewModelScope.launch {
        repositorio.salvarCondominio(Condominio(nome = nome.trim().ifBlank { null }, nomeSindica = sindica.trim(), chavePix = pix.trim(), instrucoesPagamento = instrucoes.trim()))
    }
    fun salvarUnidade(unidade: Unidade) = viewModelScope.launch { if (unidade.id == 0L) repositorio.inserirUnidade(unidade) else repositorio.atualizarUnidade(unidade) }
    fun excluirUnidade(unidade: Unidade) = viewModelScope.launch { repositorio.excluirUnidade(unidade) }
    fun salvarDespesa(despesa: Despesa) = viewModelScope.launch { if (despesa.id == 0L) repositorio.inserirDespesa(despesa) else repositorio.atualizarDespesa(despesa) }
    fun excluirDespesa(despesa: Despesa) = viewModelScope.launch { repositorio.excluirDespesa(despesa) }
    fun unidadesDaDespesa(id: Long) = repositorio.observarUnidadesDaDespesa(id)
    fun salvarRegraDaDespesa(despesa: Despesa, itens: List<DespesaUnidade>) = viewModelScope.launch { repositorio.salvarRegraDaDespesa(despesa, itens) }
    fun itensDaCobranca(id: Long) = repositorio.observarItensDaCobranca(id)
    fun item(id: Long) = repositorio.observarItem(id)
    fun regraDoItem(id: Long) = repositorio.observarRegraDoItem(id)
    fun criarRascunho(mes: Int, ano: Int, vencimento: String, usarUltima: Boolean, despesas: List<Despesa>, pronto: (Long) -> Unit) = viewModelScope.launch {
        pronto(repositorio.criarRascunho(mes, ano, vencimento, usarUltima, despesas))
    }
    fun salvarRegraDoItem(item: ItemCobranca, regras: List<ItemCobrancaRegraUnidade>, pronto: () -> Unit) = viewModelScope.launch { repositorio.salvarRegraDoItem(item, regras); pronto() }
    fun fecharCobranca(id: Long, pronto: () -> Unit) = viewModelScope.launch { repositorio.fecharCobranca(id); pronto() }
    fun detalhesDaCobranca(id: Long, pronto: (List<CobrancaUnidade>, Map<Long, List<CobrancaLinha>>) -> Unit) = viewModelScope.launch {
        val unidades = repositorio.listarUnidadesFechadas(id)
        pronto(unidades, unidades.associate { it.id to repositorio.listarLinhasDaUnidade(it.id) })
    }
    fun historicoDaUnidade(numero: String, cobrancas: List<Cobranca>, pronto: (List<Pair<Cobranca, Pair<CobrancaUnidade, List<CobrancaLinha>>>>) -> Unit) = viewModelScope.launch {
        val resultado = mutableListOf<Pair<Cobranca, Pair<CobrancaUnidade, List<CobrancaLinha>>>>()
        cobrancas.forEach { cobranca ->
            repositorio.listarUnidadesFechadas(cobranca.id).firstOrNull { it.numeroUnidade == numero }?.let { unidade ->
                resultado += cobranca to (unidade to repositorio.listarLinhasDaUnidade(unidade.id))
            }
        }
        pronto(resultado)
    }
    fun exportarHistorico(uri: Uri, contexto: android.content.Context, pronto: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val texto = montarTextoDoHistorico(repositorio.observarFechadas())
        val salvo = try {
            contexto.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(texto) } != null
        } catch (_: IOException) {
            false
        }
        withContext(Dispatchers.Main) { pronto(salvo) }
    }
    fun excluirHistorico(pronto: () -> Unit) = viewModelScope.launch {
        repositorio.excluirHistoricoFechado()
        pronto()
    }

    private suspend fun montarTextoDoHistorico(cobrancas: kotlinx.coroutines.flow.Flow<List<Cobranca>>): String {
        val resultado = StringBuilder("HISTÓRICO DE COBRANÇAS\n\n")
        cobrancas.first().forEach { cobranca ->
            resultado.append("Período: ${cobranca.mes.toString().padStart(2, '0')}/${cobranca.ano}\n")
            resultado.append("Vencimento: ${cobranca.vencimento}\n")
            repositorio.listarUnidadesFechadas(cobranca.id).forEach { unidade ->
                val linhas = repositorio.listarLinhasDaUnidade(unidade.id)
                resultado.append("\nUnidade ${unidade.numeroUnidade} — ${unidade.nomeMorador}\n")
                linhas.forEach { linha -> resultado.append("- ${linha.descricao}: ${valorFormatado(linha.valorCentavos)}\n") }
                resultado.append("Total: ${valorFormatado(linhas.sumOf { it.valorCentavos })}\n")
            }
            resultado.append("\n")
        }
        return resultado.toString()
    }
}

@Composable
fun CobrancaApp(viewModel: CobrancaViewModel) {
    val nav = rememberNavController()
    NavHost(nav, "inicio") {
        composable("inicio") {
            val condominio by viewModel.condominio.collectAsStateWithLifecycle()
            val unidades by viewModel.unidades.collectAsStateWithLifecycle()
            val despesas by viewModel.despesas.collectAsStateWithLifecycle()
            val fechadas by viewModel.cobrancasFechadas.collectAsStateWithLifecycle()
            val rascunho by viewModel.rascunho.collectAsStateWithLifecycle()
            Inicio(
                condominio = condominio,
                totalUnidades = unidades.size,
                totalDespesas = despesas.size,
                totalFechadas = fechadas.size,
                rascunho = rascunho,
                abrirCondominio = { nav.navigate("condominio") },
                abrirUnidades = { nav.navigate("unidades") },
                abrirDespesas = { nav.navigate("despesas") },
                abrirNova = { nav.navigate("nova") },
                continuarRascunho = { nav.navigate("rascunho/$it") },
                abrirHistorico = { nav.navigate("historico-unidades") },
                abrirFechadas = { nav.navigate("fechadas") },
                abrirGerenciarHistorico = { nav.navigate("historico-gerenciar") },
            )
        }
        composable("condominio") { TelaCondominio(viewModel) { nav.popBackStack() } }
        composable("unidades") { TelaUnidades(viewModel) { nav.popBackStack() } }
        composable("despesas") { TelaDespesas(viewModel, { nav.navigate("regra/$it") }) { nav.popBackStack() } }
        composable("regra/{despesaId}") { entrada ->
            val id = entrada.arguments?.getString("despesaId")?.toLongOrNull()
            TelaRegraDaDespesa(viewModel, id) { nav.popBackStack() }
        }
        composable("nova") { TelaNovaCobranca(viewModel, { nav.navigate("rascunho/$it") { popUpTo("inicio") } }) { nav.popBackStack() } }
        composable("rascunho/{cobrancaId}") { entrada ->
            TelaRascunho(
                viewModel,
                entrada.arguments?.getString("cobrancaId")?.toLongOrNull(),
                { nav.navigate("regra-item/$it") },
                { nav.navigate("imagens/$it") { popUpTo("inicio") } },
            ) { nav.popBackStack() }
        }
        composable("regra-item/{itemId}") { entrada -> TelaRegraDoItem(viewModel, entrada.arguments?.getString("itemId")?.toLongOrNull()) { nav.popBackStack() } }
        composable("fechadas") { TelaCobrancasFechadas(viewModel, { nav.navigate("imagens/$it") }) { nav.popBackStack() } }
        composable("fechada/{cobrancaId}") { entrada -> TelaDetalheCobranca(viewModel, entrada.arguments?.getString("cobrancaId")?.toLongOrNull()) { nav.popBackStack() } }
        composable("imagens/{cobrancaId}") { entrada -> TelaImagensDaCobranca(viewModel, entrada.arguments?.getString("cobrancaId")?.toLongOrNull()) { nav.popBackStack() } }
        composable("historico-unidades") { TelaHistoricoUnidades(viewModel, { nav.navigate("historico-unidade/$it") }) { nav.popBackStack() } }
        composable("historico-unidade/{numero}") { entrada -> TelaHistoricoDaUnidade(viewModel, entrada.arguments?.getString("numero").orEmpty()) { nav.popBackStack() } }
        composable("historico-gerenciar") { TelaHistoricoExportarEExcluir(viewModel) { nav.popBackStack() } }
    }
}

// ---------------------------------------------------------------------------------------------
// Tela inicial
// ---------------------------------------------------------------------------------------------

@Composable
private fun Inicio(
    condominio: Condominio?,
    totalUnidades: Int,
    totalDespesas: Int,
    totalFechadas: Int,
    rascunho: Cobranca?,
    abrirCondominio: () -> Unit,
    abrirUnidades: () -> Unit,
    abrirDespesas: () -> Unit,
    abrirNova: () -> Unit,
    continuarRascunho: (Long) -> Unit,
    abrirHistorico: () -> Unit,
    abrirFechadas: () -> Unit,
    abrirGerenciarHistorico: () -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    val sindica = condominio?.nomeSindica.orEmpty()
    val pronto = condominio != null && totalUnidades > 0 && totalDespesas > 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Cabeçalho verde com o nome do condomínio
        Column(
            Modifier
                .fillMaxWidth()
                .background(cores.primary, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                condominio?.nome?.takeIf { it.isNotBlank() } ?: "Cobrança de condomínio",
                style = MaterialTheme.typography.headlineSmall,
                color = cores.onPrimary,
            )
            Text(
                if (sindica.isNotBlank()) "Síndica: $sindica" else "Cobranças mensais por unidade",
                style = MaterialTheme.typography.bodyLarge,
                color = cores.onPrimary.copy(alpha = 0.85f),
            )
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Ação principal: criar a cobrança do mês ou continuar o rascunho
            Card(
                onClick = { if (rascunho != null) continuarRascunho(rascunho.id) else abrirNova() },
                colors = CardDefaults.cardColors(
                    containerColor = if (rascunho != null) cores.tertiaryContainer else cores.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconeRedondo(
                        icone = if (rascunho != null) Icons.Filled.Edit else Icons.Filled.Add,
                        tamanho = 52.dp,
                        fundo = if (rascunho != null) cores.tertiary else cores.primary,
                        cor = Color.White,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (rascunho != null) "Continuar cobrança de ${nomeDoMes(rascunho.mes)}" else "Nova cobrança",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (rascunho != null) cores.onTertiaryContainer else cores.onPrimaryContainer,
                        )
                        Text(
                            when {
                                rascunho != null -> "Rascunho em andamento. Toque para conferir e fechar."
                                pronto -> "Crie a cobrança do mês para todas as unidades."
                                else -> "Antes, preencha os cadastros logo abaixo."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rascunho != null) cores.onTertiaryContainer else cores.onPrimaryContainer,
                        )
                    }
                }
            }

            Secao("Cadastros", "Preencha uma vez. Só volte aqui quando algo mudar.")
            LinhaMenu(
                icone = Icons.Filled.Home,
                titulo = "Condomínio",
                descricao = if (condominio != null) "Síndica, chave PIX e instruções de pagamento" else "Informe a síndica e a chave PIX",
                aoClicar = abrirCondominio,
                concluido = condominio != null,
            )
            LinhaMenu(
                icone = Icons.Filled.Person,
                titulo = "Unidades",
                descricao = if (totalUnidades == 0) "Cadastre as unidades e os moradores" else quantidade(totalUnidades, "unidade cadastrada", "unidades cadastradas"),
                aoClicar = abrirUnidades,
                concluido = totalUnidades > 0,
            )
            LinhaMenu(
                icone = Icons.Filled.ShoppingCart,
                titulo = "Despesas",
                descricao = if (totalDespesas == 0) "Cadastre as despesas e a regra de cada uma" else quantidade(totalDespesas, "despesa cadastrada", "despesas cadastradas"),
                aoClicar = abrirDespesas,
                concluido = totalDespesas > 0,
            )

            Secao("Consultas")
            LinhaMenu(
                icone = Icons.Filled.DateRange,
                titulo = "Cobranças fechadas",
                descricao = if (totalFechadas == 0) "Nenhuma cobrança fechada ainda" else quantidade(totalFechadas, "cobrança fechada", "cobranças fechadas"),
                aoClicar = abrirFechadas,
            )
            LinhaMenu(
                icone = Icons.Filled.Search,
                titulo = "Histórico por unidade",
                descricao = "Veja o que cada unidade pagou mês a mês",
                aoClicar = abrirHistorico,
            )

            Secao("Dados")
            LinhaMenu(
                icone = Icons.Filled.Share,
                titulo = "Exportar ou excluir histórico",
                descricao = "Salve o histórico em arquivo ou apague as cobranças fechadas",
                aoClicar = abrirGerenciarHistorico,
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cadastros: condomínio, unidades e despesas
// ---------------------------------------------------------------------------------------------

@Composable
private fun TelaCondominio(vm: CobrancaViewModel, voltar: () -> Unit) {
    val salvo by vm.condominio.collectAsStateWithLifecycle()
    var nome by remember { mutableStateOf("") }
    var sindica by remember { mutableStateOf("") }
    var pix by remember { mutableStateOf("") }
    var instrucoes by remember { mutableStateOf("") }
    var tentou by remember { mutableStateOf(false) }
    LaunchedEffect(salvo) {
        salvo?.let {
            nome = it.nome.orEmpty()
            sindica = it.nomeSindica
            pix = it.chavePix
            instrucoes = it.instrucoesPagamento
        }
    }
    val salvar: () -> Unit = {
        tentou = true
        if (sindica.isNotBlank() && pix.isNotBlank()) {
            vm.salvarCondominio(nome, sindica, pix, instrucoes)
            voltar()
        }
    }
    Pagina("Condomínio", voltar, barraInferior = { BarraAcao { BotaoPrincipal("Salvar", salvar) } }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Estes dados aparecem em todas as imagens de cobrança enviadas aos moradores.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Cartao {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Campo(nome, { nome = it }, "Nome do condomínio (opcional)")
                    Campo(sindica, { sindica = it }, "Nome da síndica", tentou && sindica.isBlank())
                    Campo(pix, { pix = it }, "Chave PIX", tentou && pix.isBlank())
                    Campo(instrucoes, { instrucoes = it }, "Instruções de pagamento", linhas = 4, dica = "Ex.: enviar o comprovante pelo WhatsApp")
                }
            }
        }
    }
}

@Composable
private fun TelaUnidades(vm: CobrancaViewModel, voltar: () -> Unit) {
    val lista by vm.unidades.collectAsStateWithLifecycle()
    var editar by remember { mutableStateOf<Unidade?>(null) }
    var novo by remember { mutableStateOf(false) }
    var excluir by remember { mutableStateOf<Unidade?>(null) }
    Pagina(
        "Unidades",
        voltar,
        botaoFlutuante = {
            ExtendedFloatingActionButton(
                onClick = { novo = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar unidade") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (lista.isEmpty()) {
                item { EstadoVazio(Icons.Filled.Person, "Nenhuma unidade ainda", "Toque em \"Adicionar unidade\" para cadastrar o primeiro apartamento ou casa.") }
            }
            items(lista, { it.id }) { u -> LinhaUnidade(u, { editar = u }, { excluir = u }) }
        }
    }
    if (novo || editar != null) {
        DialogoUnidade(editar, { vm.salvarUnidade(it); novo = false; editar = null }, { novo = false; editar = null })
    }
    excluir?.let { u ->
        Confirmar(
            "Excluir unidade?",
            "A unidade ${u.numero} será removida dos cadastros. As cobranças já fechadas não mudam.",
            "Excluir",
            { vm.excluirUnidade(u); excluir = null },
            { excluir = null },
        )
    }
}

@Composable
private fun LinhaUnidade(u: Unidade, editar: () -> Unit, excluir: () -> Unit) {
    Cartao {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconeRedondo(Icons.Filled.Person)
            Column(Modifier.weight(1f)) {
                Text("Unidade ${u.numero}", style = MaterialTheme.typography.titleMedium)
                Text(
                    u.nomeMorador.ifBlank { "Sem morador" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (u.peso.compareTo(BigDecimal.ONE) != 0) {
                    Text(
                        "Peso: ${u.peso.toPlainString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Acoes(editar, excluir)
        }
    }
}

@Composable
private fun DialogoUnidade(unidade: Unidade?, salvar: (Unidade) -> Unit, fechar: () -> Unit) {
    var numero by remember(unidade) { mutableStateOf(unidade?.numero.orEmpty()) }
    var morador by remember(unidade) { mutableStateOf(unidade?.nomeMorador.orEmpty()) }
    var peso by remember(unidade) { mutableStateOf(unidade?.peso?.toPlainString() ?: "1") }
    var tentou by remember { mutableStateOf(false) }
    val pesoOk = peso.replace(',', '.').toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
    AlertDialog(
        onDismissRequest = fechar,
        title = { Text(if (unidade == null) "Adicionar unidade" else "Editar unidade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo(numero, { numero = it }, "Número da unidade", tentou && numero.isBlank())
                Campo(morador, { morador = it }, "Nome do morador")
                Campo(peso, { peso = it }, "Peso", tentou && !pesoOk, KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text(
                    "O peso só é usado no rateio proporcional. Se não souber, deixe 1.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tentou = true
                if (numero.isNotBlank() && pesoOk) {
                    salvar(Unidade(unidade?.id ?: 0, numero.trim(), morador.trim(), peso.replace(',', '.').toBigDecimal()))
                }
            }) { Text("Salvar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar") } },
    )
}

@Composable
private fun TelaDespesas(vm: CobrancaViewModel, abrirRegra: (Long) -> Unit, voltar: () -> Unit) {
    val lista by vm.despesas.collectAsStateWithLifecycle()
    var editar by remember { mutableStateOf<Despesa?>(null) }
    var novo by remember { mutableStateOf(false) }
    var excluir by remember { mutableStateOf<Despesa?>(null) }
    Pagina(
        "Despesas",
        voltar,
        botaoFlutuante = {
            ExtendedFloatingActionButton(
                onClick = { novo = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar despesa") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (lista.isEmpty()) {
                item { EstadoVazio(Icons.Filled.ShoppingCart, "Nenhuma despesa ainda", "Cadastre as despesas do condomínio, como água, luz e limpeza. Depois defina como cada uma é cobrada.") }
            } else {
                item {
                    Text(
                        "Toque em uma despesa para definir como ela é cobrada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(lista, { it.id }) { d ->
                Cartao(aoClicar = { abrirRegra(d.id) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.nome, style = MaterialTheme.typography.titleMedium)
                            Text(
                                descricaoDaRegra(d),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Acoes({ editar = d }, { excluir = d })
                    }
                }
            }
        }
    }
    if (novo || editar != null) {
        DialogoDespesa(editar, { vm.salvarDespesa(it); novo = false; editar = null }, { novo = false; editar = null })
    }
    excluir?.let { d ->
        Confirmar(
            "Excluir despesa?",
            "A despesa ${d.nome} será removida dos cadastros. As cobranças já fechadas não mudam.",
            "Excluir",
            { vm.excluirDespesa(d); excluir = null },
            { excluir = null },
        )
    }
}

@Composable
private fun DialogoDespesa(despesa: Despesa?, salvar: (Despesa) -> Unit, fechar: () -> Unit) {
    var nome by remember(despesa) { mutableStateOf(despesa?.nome.orEmpty()) }
    var tentou by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = fechar,
        title = { Text(if (despesa == null) "Adicionar despesa" else "Editar despesa") },
        text = { Campo(nome, { nome = it }, "Nome da despesa", tentou && nome.isBlank(), dica = "Ex.: Água, Luz, Limpeza") },
        confirmButton = {
            TextButton(onClick = {
                tentou = true
                if (nome.isNotBlank()) {
                    salvar(Despesa(despesa?.id ?: 0, nome.trim(), despesa?.tipoRegra ?: TipoRegra.FIXO, despesa?.valorCentavos ?: 0))
                }
            }) { Text("Salvar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Regra de cobrança (da despesa cadastrada e do item de uma cobrança)
// ---------------------------------------------------------------------------------------------

/** Opção em cartão, com bolinha de seleção, título e explicação. */
@Composable
private fun OpcaoDeEscolha(titulo: String, descricao: String, selecionada: Boolean, aoEscolher: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    Card(
        onClick = aoEscolher,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selecionada) cores.primaryContainer else cores.surface),
        border = if (selecionada) BorderStroke(2.dp, cores.primary) else BorderStroke(1.dp, cores.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = selecionada, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(descricao, style = MaterialTheme.typography.bodyMedium, color = cores.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LinhaMarcavel(texto: String, marcada: Boolean, aoAlternar: () -> Unit) {
    Cartao(aoClicar = aoAlternar) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = marcada, onCheckedChange = null)
            Text(texto, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TelaRegraDaDespesa(vm: CobrancaViewModel, despesaId: Long?, voltar: () -> Unit) {
    val despesas by vm.despesas.collectAsStateWithLifecycle()
    val despesa = despesas.firstOrNull { it.id == despesaId }
    if (despesa == null) {
        Pagina("Regra da despesa", voltar) { p -> Text("Despesa não encontrada.", Modifier.padding(p).padding(16.dp)) }
        return
    }
    val unidades by vm.unidades.collectAsStateWithLifecycle()
    val itensSalvos by vm.unidadesDaDespesa(despesa.id).collectAsStateWithLifecycle(emptyList())
    var tipo by remember(despesa.id) { mutableStateOf(despesa.tipoRegra) }
    var valor by remember(despesa.id) { mutableStateOf(centavosParaCampo(despesa.valorCentavos)) }
    var valoresPorUnidade by remember(despesa.id) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var isentas by remember(despesa.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var selecionadas by remember(despesa.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var valorParaAplicar by remember(despesa.id) { mutableStateOf("") }
    var conferiu by remember(despesa.id) { mutableStateOf(false) }
    var tentouSalvar by remember(despesa.id) { mutableStateOf(false) }

    LaunchedEffect(itensSalvos) {
        isentas = itensSalvos.filter { it.isenta }.map { it.unidadeId }.toSet()
        valoresPorUnidade = itensSalvos.associate { it.unidadeId to centavosParaCampo(it.valorCentavos) }
    }
    val valorCentavos = valor.paraCentavos()
    val faltamValores = tipo == TipoRegra.POR_UNIDADE && unidades.any { it.id !in isentas && valoresPorUnidade[it.id].orEmpty().paraCentavos() == null }
    val podeSalvar = (tipo == TipoRegra.POR_UNIDADE || valorCentavos != null) && !faltamValores
    val calculo = if (!faltamValores && (tipo == TipoRegra.POR_UNIDADE || valorCentavos != null)) {
        CalculadoraDeRegras.calcular(tipo, valorCentavos ?: 0, unidades.map { u -> UnidadeDaRegra(u.id, u.id in isentas, valoresPorUnidade[u.id].orEmpty().paraCentavos() ?: 0, u.peso) })
    } else emptyMap()

    val salvar: () -> Unit = {
        tentouSalvar = true
        if (podeSalvar) {
            val itens = unidades.mapNotNull { unidade ->
                val textoDoValor = valoresPorUnidade[unidade.id]
                if (unidade.id in isentas || (tipo == TipoRegra.POR_UNIDADE && textoDoValor?.paraCentavos() != null)) {
                    DespesaUnidade(despesa.id, unidade.id, unidade.id in isentas, textoDoValor?.paraCentavos() ?: 0)
                } else null
            }
            vm.salvarRegraDaDespesa(despesa.copy(tipoRegra = tipo, valorCentavos = valorCentavos ?: 0), itens)
            voltar()
        }
    }

    Pagina("Regra da despesa", voltar, barraInferior = { BarraAcao { BotaoPrincipal("Salvar regra", salvar) } }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(despesa.nome, style = MaterialTheme.typography.titleLarge) }
            item { Secao("Como cobrar esta despesa?") }
            items(TipoRegra.values().toList(), { it.name }) { opcao ->
                OpcaoDeEscolha(nomeDoTipo(opcao), explicacaoDoTipo(opcao), tipo == opcao) { tipo = opcao; conferiu = false }
            }
            if (tipo != TipoRegra.POR_UNIDADE) {
                item {
                    Cartao {
                        Column(Modifier.padding(16.dp)) {
                            Campo(
                                valor,
                                { valor = it; conferiu = false },
                                if (tipo == TipoRegra.FIXO) "Valor por unidade (R$)" else "Valor total a ratear (R$)",
                                tentouSalvar && valorCentavos == null,
                                KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
            }
            if (tipo == TipoRegra.POR_UNIDADE) {
                item {
                    Cartao {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Preencher várias de uma vez", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Marque as unidades abaixo, digite o valor e toque em Aplicar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Campo(valorParaAplicar, { valorParaAplicar = it }, "Valor para aplicar (R$)", teclado = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            BotaoSecundario(
                                "Aplicar às unidades marcadas",
                                {
                                    valorParaAplicar.paraCentavos()?.let { centavos ->
                                        valoresPorUnidade = valoresPorUnidade + selecionadas.associateWith { centavosParaCampo(centavos) }
                                        valorParaAplicar = ""
                                        conferiu = false
                                    }
                                },
                                habilitado = selecionadas.isNotEmpty() && valorParaAplicar.paraCentavos() != null,
                            )
                        }
                    }
                }
            }
            item { Secao("Unidades", "Use \"Isenta\" para quem não paga esta despesa.") }
            items(unidades, { it.id }) { unidade ->
                Cartao {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Unidade ${unidade.numero}" + if (unidade.nomeMorador.isNotBlank()) " — ${unidade.nomeMorador}" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Switch(
                                checked = unidade.id in isentas,
                                onCheckedChange = { marcada -> isentas = if (marcada) isentas + unidade.id else isentas - unidade.id; conferiu = false },
                            )
                            Text("Isenta (não paga)", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (tipo == TipoRegra.POR_UNIDADE) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Checkbox(
                                    checked = unidade.id in selecionadas,
                                    onCheckedChange = { marcada -> selecionadas = if (marcada) selecionadas + unidade.id else selecionadas - unidade.id },
                                )
                                Text("Marcar para aplicar valor acima", style = MaterialTheme.typography.bodyMedium)
                            }
                            Campo(
                                valoresPorUnidade[unidade.id].orEmpty(),
                                { novo -> valoresPorUnidade = valoresPorUnidade + (unidade.id to novo); conferiu = false },
                                "Valor desta unidade (R$)",
                                tentouSalvar && unidade.id !in isentas && valoresPorUnidade[unidade.id].orEmpty().paraCentavos() == null,
                                KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        if (tipo == TipoRegra.PROPORCIONAL) {
                            Text("Peso: ${unidade.peso.toPlainString()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { BotaoSecundario("Conferir valores", { conferiu = true }) }
            if (conferiu) item { Conferencia(unidades, calculo, faltamValores) }
        }
    }
}

@Composable
private fun Conferencia(unidades: List<Unidade>, valores: Map<Long, Long>, faltamValores: Boolean) {
    Cartao(cor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Conferência", style = MaterialTheme.typography.titleMedium)
            if (faltamValores) {
                Text("Informe o valor de todas as unidades não isentas.", color = MaterialTheme.colorScheme.error)
            } else {
                unidades.forEach { unidade -> LinhaValor("Unidade ${unidade.numero}", valorFormatado(valores[unidade.id] ?: 0)) }
                HorizontalDivider()
                LinhaValor("Total", valorFormatado(valores.values.sum()), destaque = true)
            }
        }
    }
}

@Composable
private fun TelaRegraDoItem(vm: CobrancaViewModel, itemId: Long?, voltar: () -> Unit) {
    if (itemId == null) return
    val item by vm.item(itemId).collectAsStateWithLifecycle(null)
    val unidades by vm.unidades.collectAsStateWithLifecycle()
    val salvas by vm.regraDoItem(itemId).collectAsStateWithLifecycle(emptyList())
    val atual = item ?: return
    var tipo by remember(itemId) { mutableStateOf(atual.tipoRegra) }
    var valor by remember(itemId) { mutableStateOf(centavosParaCampo(atual.valorCentavos)) }
    var valores by remember(itemId) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var isentas by remember(itemId) { mutableStateOf<Set<Long>>(emptySet()) }
    var tentou by remember { mutableStateOf(false) }
    LaunchedEffect(salvas) {
        isentas = salvas.filter { it.isenta }.map { it.unidadeId }.toSet()
        valores = salvas.associate { it.unidadeId to centavosParaCampo(it.valorCentavos) }
    }
    val centavos = valor.paraCentavos()
    val incompleto = tipo == TipoRegra.POR_UNIDADE && unidades.any { it.id !in isentas && valores[it.id].orEmpty().paraCentavos() == null }
    val salvar: () -> Unit = {
        tentou = true
        if (!incompleto && (tipo == TipoRegra.POR_UNIDADE || centavos != null)) {
            val regras = unidades.map { u -> ItemCobrancaRegraUnidade(itemId, u.id, u.id in isentas, valores[u.id].orEmpty().paraCentavos() ?: 0) }
            vm.salvarRegraDoItem(atual.copy(tipoRegra = tipo, valorCentavos = centavos ?: 0), regras, voltar)
        }
    }
    Pagina("Ajustar item", voltar, barraInferior = { BarraAcao { BotaoPrincipal("Salvar", salvar) } }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(atual.nome, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Esta mudança vale só para esta cobrança. O cadastro da despesa não é alterado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Secao("Como cobrar?") }
            items(TipoRegra.values().toList(), { it.name }) { opcao ->
                OpcaoDeEscolha(nomeDoTipo(opcao), explicacaoDoTipo(opcao), tipo == opcao) { tipo = opcao }
            }
            if (tipo != TipoRegra.POR_UNIDADE) {
                item {
                    Cartao {
                        Column(Modifier.padding(16.dp)) {
                            Campo(
                                valor,
                                { valor = it },
                                if (tipo == TipoRegra.FIXO) "Valor por unidade (R$)" else "Valor total a ratear (R$)",
                                tentou && centavos == null,
                                KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
            }
            item { Secao("Unidades", "Use \"Isenta\" para quem não paga este item.") }
            items(unidades, { it.id }) { u ->
                Cartao {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Unidade ${u.numero}" + if (u.nomeMorador.isNotBlank()) " — ${u.nomeMorador}" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Switch(
                                checked = u.id in isentas,
                                onCheckedChange = { b -> isentas = if (b) isentas + u.id else isentas - u.id },
                            )
                            Text("Isenta (não paga)", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (tipo == TipoRegra.POR_UNIDADE) {
                            Campo(
                                valores[u.id].orEmpty(),
                                { v -> valores = valores + (u.id to v) },
                                "Valor desta unidade (R$)",
                                tentou && u.id !in isentas && valores[u.id].orEmpty().paraCentavos() == null,
                                KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        if (tipo == TipoRegra.PROPORCIONAL) {
                            Text("Peso: ${u.peso.toPlainString()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Nova cobrança e rascunho
// ---------------------------------------------------------------------------------------------

@Composable
private fun TelaNovaCobranca(vm: CobrancaViewModel, abrir: (Long) -> Unit, voltar: () -> Unit) {
    val despesas by vm.despesas.collectAsStateWithLifecycle()
    val fechadas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    val rascunho by vm.rascunho.collectAsStateWithLifecycle()
    var mes by remember { mutableStateOf("") }
    var ano by remember { mutableStateOf("") }
    var vencimento by remember { mutableStateOf("") }
    var usarUltima by remember { mutableStateOf(fechadas.isNotEmpty()) }
    var selecionadas by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var tentou by remember { mutableStateOf(false) }
    // Quando o histórico termina de carregar e já existe cobrança fechada, começa partindo dela.
    LaunchedEffect(fechadas.isNotEmpty()) { if (fechadas.isNotEmpty()) usarUltima = true }

    val mesNumero = mes.toIntOrNull()
    val anoNumero = ano.toIntOrNull()
    val mesInvalido = mesNumero == null || mesNumero !in 1..12
    val anoInvalido = anoNumero == null || anoNumero < 2000
    val faltaItens = !usarUltima && selecionadas.isEmpty()
    val criar: () -> Unit = {
        tentou = true
        if (mesNumero != null && mesNumero in 1..12 && anoNumero != null && anoNumero >= 2000 && vencimento.isNotBlank() && !faltaItens) {
            vm.criarRascunho(mesNumero, anoNumero, vencimento.trim(), usarUltima, despesas.filter { it.id in selecionadas }, abrir)
        }
    }

    Pagina(
        "Nova cobrança",
        voltar,
        barraInferior = { if (rascunho == null) BarraAcao { BotaoPrincipal("Criar rascunho", criar) } },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val existente = rascunho
            if (existente != null) {
                item {
                    Cartao(cor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Já existe uma cobrança em andamento", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                "Referência: ${nomeDoMes(existente.mes)} de ${existente.ano}. Termine ou feche essa cobrança antes de criar outra.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            BotaoPrincipal("Abrir rascunho", { abrir(existente.id) })
                        }
                    }
                }
            } else {
                item { Secao("Período", "Para qual mês é esta cobrança?") }
                item {
                    Cartao {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Campo(mes, { mes = it }, "Mês (1 a 12)", tentou && mesInvalido, KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                Campo(ano, { ano = it }, "Ano", tentou && anoInvalido, KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            }
                            Campo(vencimento, { vencimento = it }, "Vencimento", tentou && vencimento.isBlank(), dica = "Ex.: 10/01/2027")
                        }
                    }
                }
                item { Secao("Itens da cobrança", "O que será cobrado neste mês?") }
                if (fechadas.isNotEmpty()) {
                    item {
                        OpcaoDeEscolha(
                            "Partir da última cobrança fechada",
                            "Copia os itens e as regras do mês anterior. Você ajusta o que precisar na conferência.",
                            usarUltima,
                        ) { usarUltima = true }
                    }
                }
                item {
                    OpcaoDeEscolha(
                        "Escolher entre as despesas cadastradas",
                        "Marque as despesas que entram nesta cobrança.",
                        !usarUltima,
                    ) { usarUltima = false }
                }
                if (!usarUltima) {
                    if (despesas.isEmpty()) {
                        item {
                            Text(
                                "Você ainda não cadastrou despesas. Volte ao início e cadastre em Despesas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(despesas, { it.id }) { d ->
                        LinhaMarcavel(d.nome, d.id in selecionadas) {
                            selecionadas = if (d.id in selecionadas) selecionadas - d.id else selecionadas + d.id
                        }
                    }
                    if (tentou && faltaItens && despesas.isNotEmpty()) {
                        item { Text("Marque pelo menos uma despesa.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

private fun calcularItem(item: ItemCobranca, unidades: List<Unidade>, regras: List<ItemCobrancaRegraUnidade>) = CalculadoraDeRegras.calcular(item.tipoRegra, item.valorCentavos, unidades.map { u -> regras.firstOrNull { it.unidadeId == u.id }.let { r -> UnidadeDaRegra(u.id, r?.isenta ?: false, r?.valorCentavos ?: 0, u.peso) } })

@Composable
private fun TelaRascunho(vm: CobrancaViewModel, cobrancaId: Long?, editar: (Long) -> Unit, aoFechar: (Long) -> Unit, voltar: () -> Unit) {
    if (cobrancaId == null) return
    val itens by vm.itensDaCobranca(cobrancaId).collectAsStateWithLifecycle(emptyList())
    val unidades by vm.unidades.collectAsStateWithLifecycle()
    val rascunho by vm.rascunho.collectAsStateWithLifecycle()
    val calculos = itens.map { item ->
        val regras by vm.regraDoItem(item.id).collectAsStateWithLifecycle(emptyList())
        item to calcularItem(item, unidades, regras)
    }
    var confirmar by remember { mutableStateOf(false) }
    val totalGeral = calculos.sumOf { it.second.values.sum() }

    Pagina(
        "Conferir cobrança",
        voltar,
        barraInferior = {
            BarraAcao {
                BotaoPrincipal("Fechar cobrança", { confirmar = true }, habilitado = itens.isNotEmpty() && unidades.isNotEmpty())
            }
        },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val atual = rascunho?.takeIf { it.id == cobrancaId }
                Cartao(cor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (atual != null) {
                            Text("${nomeDoMes(atual.mes)} de ${atual.ano}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Vencimento: ${atual.vencimento}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text("Total geral", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(valorFormatado(totalGeral), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Text(
                    "Confira os valores antes de fechar. Depois de fechada, a cobrança não pode ser editada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Secao("Itens da cobrança", "Toque em um item para ajustar a regra ou o valor.") }
            if (itens.isEmpty()) {
                item { Text("Esta cobrança não tem itens.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(calculos, key = { "item-${it.first.id}" }) { (item, valores) ->
                Cartao(aoClicar = { editar(item.id) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.nome, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${nomeDoTipo(item.tipoRegra)} · total ${valorFormatado(valores.values.sum())}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            item { Secao("Valor por unidade") }
            if (unidades.isEmpty()) {
                item { Text("Cadastre as unidades antes de fechar a cobrança.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            items(unidades, key = { "unidade-${it.id}" }) { unidade ->
                val itensDaUnidade = calculos.mapNotNull { (item, valores) -> valores[unidade.id]?.takeIf { it > 0 }?.let { item.nome to it } }
                Cartao {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Unidade ${unidade.numero}", style = MaterialTheme.typography.titleMedium)
                        if (unidade.nomeMorador.isNotBlank()) {
                            Text(unidade.nomeMorador, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        itensDaUnidade.forEach { (nome, valor) -> LinhaValor(nome, valorFormatado(valor)) }
                        if (itensDaUnidade.isEmpty()) {
                            Text("Nada a cobrar desta unidade.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                        LinhaValor("Total", valorFormatado(itensDaUnidade.sumOf { it.second }), destaque = true)
                    }
                }
            }
        }
    }
    if (confirmar) {
        Confirmar(
            "Fechar cobrança?",
            "Depois de fechada, esta cobrança não poderá ser editada. As imagens serão geradas com estes valores.",
            "Fechar cobrança",
            { vm.fecharCobranca(cobrancaId) { aoFechar(cobrancaId) }; confirmar = false },
            { confirmar = false },
            perigo = false,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Cobranças fechadas, imagens e histórico
// ---------------------------------------------------------------------------------------------

@Composable
private fun TelaCobrancasFechadas(vm: CobrancaViewModel, abrir: (Long) -> Unit, voltar: () -> Unit) {
    val cobrancas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    Pagina("Cobranças fechadas", voltar) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (cobrancas.isEmpty()) {
                item { EstadoVazio(Icons.Filled.DateRange, "Nenhuma cobrança fechada", "Quando você fechar a primeira cobrança do mês, ela aparece aqui para ver e compartilhar as imagens.") }
            } else {
                item {
                    Text(
                        "Toque em um mês para ver e compartilhar as imagens.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(cobrancas, { it.id }) { c ->
                Cartao(aoClicar = { abrir(c.id) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${nomeDoMes(c.mes)} de ${c.ano}", style = MaterialTheme.typography.titleMedium)
                            Text("Vencimento: ${c.vencimento}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelaDetalheCobranca(vm: CobrancaViewModel, cobrancaId: Long?, voltar: () -> Unit) {
    var unidades by remember { mutableStateOf<List<CobrancaUnidade>>(emptyList()) }
    var linhas by remember { mutableStateOf<Map<Long, List<CobrancaLinha>>>(emptyMap()) }
    LaunchedEffect(cobrancaId) { cobrancaId?.let { vm.detalhesDaCobranca(it) { u, l -> unidades = u; linhas = l } } }
    Pagina("Unidades da cobrança", voltar) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(unidades, { it.id }) { u ->
                val total = linhas[u.id].orEmpty().sumOf { it.valorCentavos }
                Cartao {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Unidade ${u.numeroUnidade} — ${u.nomeMorador}", style = MaterialTheme.typography.titleMedium)
                        LinhaValor("Total", valorFormatado(total), destaque = true)
                    }
                }
            }
            if (unidades.isEmpty()) item { Text("Carregando unidades...") }
        }
    }
}

@Composable
private fun TelaImagensDaCobranca(vm: CobrancaViewModel, cobrancaId: Long?, voltar: () -> Unit) {
    val cobrancas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    val cobranca = cobrancas.firstOrNull { it.id == cobrancaId }
    var unidades by remember(cobrancaId) { mutableStateOf<List<CobrancaUnidade>>(emptyList()) }
    var linhas by remember(cobrancaId) { mutableStateOf<Map<Long, List<CobrancaLinha>>>(emptyMap()) }
    LaunchedEffect(cobrancaId) { cobrancaId?.let { vm.detalhesDaCobranca(it) { u, l -> unidades = u; linhas = l } } }
    Pagina(if (cobranca != null) "Imagens de ${nomeDoMes(cobranca.mes)}" else "Imagens da cobrança", voltar) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (cobranca != null) {
                item {
                    Text(
                        "Toque em Compartilhar para enviar a imagem de cada unidade pelo WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(unidades, { it.id }) { unidade -> PreviaDaImagem(cobranca, unidade, linhas[unidade.id].orEmpty()) }
            } else {
                item { Text("Carregando cobrança...", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun PreviaDaImagem(cobranca: Cobranca, unidade: CobrancaUnidade, linhas: List<CobrancaLinha>) {
    val contexto = LocalContext.current
    val gerador = remember { GeradorDeImagem() }
    var arquivo by remember(cobranca.id, unidade.id) { mutableStateOf<java.io.File?>(null) }
    var bitmap by remember(cobranca.id, unidade.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(cobranca, unidade, linhas) {
        val criado = withContext(Dispatchers.IO) { gerador.gerar(contexto, cobranca, unidade, linhas) }
        arquivo = criado
        bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(criado.absolutePath) }
    }
    Cartao {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Unidade ${unidade.numeroUnidade}" + if (unidade.nomeMorador.isNotBlank()) " — ${unidade.nomeMorador}" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            val imagem = bitmap
            if (imagem != null) {
                Image(
                    bitmap = imagem.asImageBitmap(),
                    contentDescription = "Prévia da cobrança",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(imagem.width.toFloat() / imagem.height.toFloat())
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            BotaoPrincipal("Compartilhar", { arquivo?.let { gerador.compartilhar(contexto, it) } }, habilitado = arquivo != null, icone = Icons.Filled.Share)
        }
    }
}

@Composable
private fun TelaHistoricoUnidades(vm: CobrancaViewModel, abrir: (String) -> Unit, voltar: () -> Unit) {
    val unidades by vm.unidades.collectAsStateWithLifecycle()
    Pagina("Histórico por unidade", voltar) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (unidades.isEmpty()) {
                item { EstadoVazio(Icons.Filled.Person, "Nenhuma unidade cadastrada", "Cadastre as unidades para consultar o histórico de cada uma.") }
            } else {
                item {
                    Text(
                        "Escolha a unidade para ver as cobranças fechadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(unidades, { it.id }) { u ->
                LinhaMenu(
                    icone = Icons.Filled.Person,
                    titulo = "Unidade ${u.numero}",
                    descricao = u.nomeMorador.ifBlank { "Sem morador" },
                    aoClicar = { abrir(u.numero) },
                )
            }
        }
    }
}

@Composable
private fun TelaHistoricoDaUnidade(vm: CobrancaViewModel, numero: String, voltar: () -> Unit) {
    val cobrancas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    var resultado by remember(numero, cobrancas) { mutableStateOf<List<Pair<Cobranca, Pair<CobrancaUnidade, List<CobrancaLinha>>>>>(emptyList()) }
    LaunchedEffect(numero, cobrancas) {
        vm.historicoDaUnidade(numero, cobrancas) { resultado = it }
    }
    Pagina("Unidade $numero", voltar) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (resultado.isEmpty()) {
                item { EstadoVazio(Icons.Filled.DateRange, "Nada por aqui ainda", "Não há cobranças fechadas para esta unidade.") }
            }
            items(resultado, { it.first.id }) { (c, dados) ->
                Cartao {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${nomeDoMes(c.mes)} de ${c.ano}", style = MaterialTheme.typography.titleMedium)
                        Text("Vencimento: ${c.vencimento}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        dados.second.forEach { LinhaValor(it.descricao, valorFormatado(it.valorCentavos)) }
                        HorizontalDivider()
                        LinhaValor("Total", valorFormatado(dados.second.sumOf { it.valorCentavos }), destaque = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelaHistoricoExportarEExcluir(vm: CobrancaViewModel, voltar: () -> Unit) {
    val contexto = LocalContext.current
    val cobrancas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    var confirmarExclusao by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    val criarDocumento = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) vm.exportarHistorico(uri, contexto) { salvo ->
            mensagem = if (salvo) "Histórico exportado." else "Não foi possível salvar o arquivo."
        }
    }

    Pagina("Exportar ou excluir", voltar) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cartao {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exportar histórico resumido", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Gera um arquivo de texto com as cobranças fechadas por unidade, incluindo período, vencimento, itens e total.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BotaoPrincipal("Exportar arquivo .txt", { criarDocumento.launch("historico-cobrancas.txt") }, icone = Icons.Filled.Share)
                    mensagem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }
            }
            Cartao {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Excluir histórico", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Remove todas as cobranças fechadas deste aparelho. Os cadastros não são apagados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { confirmarExclusao = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = cobrancas.isNotEmpty(),
                    ) { Text("Excluir histórico", style = MaterialTheme.typography.labelLarge) }
                    if (cobrancas.isEmpty()) {
                        Text("Não há cobranças fechadas para excluir.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (confirmarExclusao) {
        Confirmar(
            "Excluir histórico?",
            "Serão apagadas ${cobrancas.size} cobrança(s) fechada(s), com suas unidades, itens e linhas. " +
                "Não serão apagados o condomínio, as unidades, as despesas nem o rascunho atual. " +
                "Esta ação não pode ser desfeita e não tem relação com imagens já enviadas pelo WhatsApp.",
            "Excluir histórico",
            { vm.excluirHistorico { confirmarExclusao = false } },
            { confirmarExclusao = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Textos e formatação
// ---------------------------------------------------------------------------------------------

private fun nomeDoTipo(tipo: TipoRegra) = when (tipo) {
    TipoRegra.FIXO -> "Valor fixo por unidade"
    TipoRegra.POR_UNIDADE -> "Valores diferentes por grupo ou unidade"
    TipoRegra.IGUALITARIO -> "Rateio igualitário"
    TipoRegra.PROPORCIONAL -> "Rateio proporcional"
}

private fun explicacaoDoTipo(tipo: TipoRegra) = when (tipo) {
    TipoRegra.FIXO -> "Todas as unidades pagam o mesmo valor."
    TipoRegra.POR_UNIDADE -> "Cada unidade ou grupo paga um valor diferente."
    TipoRegra.IGUALITARIO -> "O valor total é dividido igualmente entre as unidades."
    TipoRegra.PROPORCIONAL -> "O valor total é dividido conforme o peso de cada unidade."
}

/** Resumo de uma linha para a lista de despesas. */
private fun descricaoDaRegra(d: Despesa): String = when {
    d.tipoRegra == TipoRegra.POR_UNIDADE -> "Valores diferentes por unidade"
    d.valorCentavos == 0L -> "Regra ainda não definida. Toque para definir."
    d.tipoRegra == TipoRegra.FIXO -> "${valorFormatado(d.valorCentavos)} por unidade"
    d.tipoRegra == TipoRegra.IGUALITARIO -> "${valorFormatado(d.valorCentavos)} divididos igualmente"
    else -> "${valorFormatado(d.valorCentavos)} divididos por peso"
}

private fun nomeDoMes(mes: Int): String = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
).getOrNull(mes - 1) ?: mes.toString()

private fun quantidade(total: Int, singular: String, plural: String) = if (total == 1) "1 $singular" else "$total $plural"

private fun String.paraCentavos(): Long? = try {
    if (isBlank()) null else BigDecimal(trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact().takeIf { it >= 0 }
} catch (_: NumberFormatException) { null } catch (_: ArithmeticException) { null }

private fun centavosParaCampo(centavos: Long): String = BigDecimal.valueOf(centavos).movePointLeft(2).setScale(2).toPlainString().replace('.', ',')

private fun valorFormatado(centavos: Long): String = "R$ ${centavosParaCampo(centavos)}"
