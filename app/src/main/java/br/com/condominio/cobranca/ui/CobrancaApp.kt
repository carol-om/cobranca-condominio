package br.com.condominio.cobranca.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CobrancaViewModel(private val repositorio: CobrancaRepositorio) : ViewModel() {
    val condominio = repositorio.observarCondominio().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val unidades = repositorio.observarUnidades().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val despesas = repositorio.observarDespesas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun salvarCondominio(nome: String, sindica: String, pix: String, instrucoes: String) = viewModelScope.launch {
        repositorio.salvarCondominio(Condominio(nome = nome.trim().ifBlank { null }, nomeSindica = sindica.trim(), chavePix = pix.trim(), instrucoesPagamento = instrucoes.trim()))
    }
    fun salvarUnidade(unidade: Unidade) = viewModelScope.launch { if (unidade.id == 0L) repositorio.inserirUnidade(unidade) else repositorio.atualizarUnidade(unidade) }
    fun excluirUnidade(unidade: Unidade) = viewModelScope.launch { repositorio.excluirUnidade(unidade) }
    fun salvarDespesa(despesa: Despesa) = viewModelScope.launch { if (despesa.id == 0L) repositorio.inserirDespesa(despesa) else repositorio.atualizarDespesa(despesa) }
    fun excluirDespesa(despesa: Despesa) = viewModelScope.launch { repositorio.excluirDespesa(despesa) }
    fun unidadesDaDespesa(id: Long) = repositorio.observarUnidadesDaDespesa(id)
    fun salvarRegraDaDespesa(despesa: Despesa, itens: List<DespesaUnidade>) = viewModelScope.launch { repositorio.salvarRegraDaDespesa(despesa, itens) }
}

@Composable fun CobrancaApp(viewModel: CobrancaViewModel) {
    val nav = rememberNavController()
    NavHost(nav, "inicio") {
        composable("inicio") { Inicio({ nav.navigate("condominio") }, { nav.navigate("unidades") }, { nav.navigate("despesas") }) }
        composable("condominio") { TelaCondominio(viewModel) { nav.popBackStack() } }
        composable("unidades") { TelaUnidades(viewModel) { nav.popBackStack() } }
        composable("despesas") { TelaDespesas(viewModel, { nav.navigate("regra/$it") }) { nav.popBackStack() } }
        composable("regra/{despesaId}") { entrada ->
            val id = entrada.arguments?.getString("despesaId")?.toLongOrNull()
            TelaRegraDaDespesa(viewModel, id) { nav.popBackStack() }
        }
    }
}

@Composable private fun Inicio(condominio: () -> Unit, unidades: () -> Unit, despesas: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text("Cobrança de condomínio")
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(condominio, Modifier.fillMaxWidth()) { Text("Condomínio") }
        Button(unidades, Modifier.fillMaxWidth()) { Text("Unidades") }
        Button(despesas, Modifier.fillMaxWidth()) { Text("Despesas") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TelaCondominio(vm: CobrancaViewModel, voltar: () -> Unit) {
    val salvo by vm.condominio.collectAsStateWithLifecycle()
    var nome by remember { mutableStateOf("") }; var sindica by remember { mutableStateOf("") }; var pix by remember { mutableStateOf("") }; var instrucoes by remember { mutableStateOf("") }; var tentou by remember { mutableStateOf(false) }
    LaunchedEffect(salvo) { salvo?.let { nome = it.nome.orEmpty(); sindica = it.nomeSindica; pix = it.chavePix; instrucoes = it.instrucoesPagamento } }
    Pagina("Condomínio", voltar) { p -> Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Campo(nome, { nome = it }, "Nome do condomínio (opcional)")
        Campo(sindica, { sindica = it }, "Nome da síndica", tentou && sindica.isBlank())
        Campo(pix, { pix = it }, "Chave PIX", tentou && pix.isBlank())
        Campo(instrucoes, { instrucoes = it }, "Instruções de pagamento", linhas = 4)
        Button(onClick = { tentou = true; if (sindica.isNotBlank() && pix.isNotBlank()) vm.salvarCondominio(nome, sindica, pix, instrucoes) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
    } }
}

@Composable private fun TelaUnidades(vm: CobrancaViewModel, voltar: () -> Unit) {
    val lista by vm.unidades.collectAsStateWithLifecycle(); var editar by remember { mutableStateOf<Unidade?>(null) }; var novo by remember { mutableStateOf(false) }; var excluir by remember { mutableStateOf<Unidade?>(null) }
    Pagina("Unidades", voltar, { Button(onClick = { novo = true }) { Text("Adicionar") } }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(lista, { it.id }) { u -> LinhaUnidade(u, { editar = u }, { excluir = u }) } }
    }
    if (novo || editar != null) DialogoUnidade(editar, { vm.salvarUnidade(it); novo = false; editar = null }, { novo = false; editar = null })
    excluir?.let { u -> Confirmar("Excluir a unidade ${u.numero}?", { vm.excluirUnidade(u); excluir = null }, { excluir = null }) }
}

@Composable private fun LinhaUnidade(u: Unidade, editar: () -> Unit, excluir: () -> Unit) = Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Column { Text("Unidade ${u.numero}"); Text(u.nomeMorador.ifBlank { "Sem morador" }); Text("Peso: ${u.peso}") }; Acoes(editar, excluir)
} }

@Composable private fun TelaDespesas(vm: CobrancaViewModel, abrirRegra: (Long) -> Unit, voltar: () -> Unit) {
    val lista by vm.despesas.collectAsStateWithLifecycle(); var editar by remember { mutableStateOf<Despesa?>(null) }; var novo by remember { mutableStateOf(false) }; var excluir by remember { mutableStateOf<Despesa?>(null) }
    Pagina("Despesas", voltar, { Button(onClick = { novo = true }) { Text("Adicionar") } }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(lista, { it.id }) { d -> Card(Modifier.fillMaxWidth().clickable { abrirRegra(d.id) }) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(d.nome); Acoes({ editar = d }, { excluir = d }) } } } }
    }
    if (novo || editar != null) DialogoDespesa(editar, { vm.salvarDespesa(it); novo = false; editar = null }, { novo = false; editar = null })
    excluir?.let { d -> Confirmar("Excluir a despesa ${d.nome}?", { vm.excluirDespesa(d); excluir = null }, { excluir = null }) }
}

@Composable private fun TelaRegraDaDespesa(vm: CobrancaViewModel, despesaId: Long?, voltar: () -> Unit) {
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

    Pagina("Regra da despesa", voltar) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(despesa.nome, style = MaterialTheme.typography.titleMedium) }
            item { TipoRegra.values().forEach { opcao -> Row(Modifier.fillMaxWidth().clickable { tipo = opcao; conferiu = false }.padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(tipo == opcao, { tipo = opcao; conferiu = false }); Text(nomeDoTipo(opcao)) } } }
            if (tipo != TipoRegra.POR_UNIDADE) item { Campo(valor, { valor = it; conferiu = false }, if (tipo == TipoRegra.FIXO) "Valor por unidade" else "Valor total a ratear", tentouSalvar && valorCentavos == null, KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            if (tipo == TipoRegra.POR_UNIDADE) {
                item { Campo(valorParaAplicar, { valorParaAplicar = it }, "Valor para aplicar às selecionadas", teclado = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                item { Button(onClick = { valorParaAplicar.paraCentavos()?.let { centavos -> valoresPorUnidade = valoresPorUnidade + selecionadas.associateWith { centavosParaCampo(centavos) }; valorParaAplicar = ""; conferiu = false } }, modifier = Modifier.fillMaxWidth(), enabled = selecionadas.isNotEmpty() && valorParaAplicar.paraCentavos() != null) { Text("Aplicar este valor às unidades selecionadas") } }
            }
            item { Text("Unidades", style = MaterialTheme.typography.titleMedium) }
            items(unidades, { it.id }) { unidade ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Unidade ${unidade.numero} — ${unidade.nomeMorador.ifBlank { "Sem morador" }}")
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(unidade.id in isentas, { marcada -> isentas = if (marcada) isentas + unidade.id else isentas - unidade.id; conferiu = false }); Text("Isenta") }
                    if (tipo == TipoRegra.POR_UNIDADE) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(unidade.id in selecionadas, { marcada -> selecionadas = if (marcada) selecionadas + unidade.id else selecionadas - unidade.id }); Text("Selecionar para aplicar valor") }
                        Campo(valoresPorUnidade[unidade.id].orEmpty(), { novo -> valoresPorUnidade = valoresPorUnidade + (unidade.id to novo); conferiu = false }, "Valor desta unidade", tentouSalvar && unidade.id !in isentas && valoresPorUnidade[unidade.id].orEmpty().paraCentavos() == null, KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    if (tipo == TipoRegra.PROPORCIONAL) Text("Peso: ${unidade.peso}")
                } }
            }
            item { Button(onClick = { conferiu = true }, modifier = Modifier.fillMaxWidth()) { Text("Conferir") } }
            if (conferiu) item { Conferencia(unidades, calculo, faltamValores) }
            item { Button(onClick = {
                tentouSalvar = true
                if (podeSalvar) {
                    val itens = unidades.mapNotNull { unidade ->
                        val textoDoValor = valoresPorUnidade[unidade.id]
                        if (unidade.id in isentas || (tipo == TipoRegra.POR_UNIDADE && textoDoValor?.paraCentavos() != null)) DespesaUnidade(despesa.id, unidade.id, unidade.id in isentas, textoDoValor?.paraCentavos() ?: 0) else null
                    }
                    vm.salvarRegraDaDespesa(despesa.copy(tipoRegra = tipo, valorCentavos = valorCentavos ?: 0), itens)
                    voltar()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar regra") } }
        }
    }
}

@Composable private fun Conferencia(unidades: List<Unidade>, valores: Map<Long, Long>, faltamValores: Boolean) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("Conferência", style = MaterialTheme.typography.titleMedium)
    if (faltamValores) Text("Informe o valor de todas as unidades não isentas.", color = MaterialTheme.colorScheme.error)
    else {
        unidades.forEach { unidade -> Text("Unidade ${unidade.numero} → ${valorFormatado(valores[unidade.id] ?: 0)}") }
        Text("Total: ${valorFormatado(valores.values.sum())}")
    }
} }

private fun nomeDoTipo(tipo: TipoRegra) = when (tipo) {
    TipoRegra.FIXO -> "Valor fixo por unidade"
    TipoRegra.POR_UNIDADE -> "Valores diferentes por grupo ou unidade"
    TipoRegra.IGUALITARIO -> "Rateio igualitário"
    TipoRegra.PROPORCIONAL -> "Rateio proporcional"
}

private fun String.paraCentavos(): Long? = try {
    if (isBlank()) null else BigDecimal(trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact().takeIf { it >= 0 }
} catch (_: NumberFormatException) { null } catch (_: ArithmeticException) { null }

private fun centavosParaCampo(centavos: Long): String = BigDecimal.valueOf(centavos).movePointLeft(2).setScale(2).toPlainString().replace('.', ',')

private fun valorFormatado(centavos: Long): String = "R$ ${centavosParaCampo(centavos)}"

@Composable private fun Acoes(editar: () -> Unit, excluir: () -> Unit) = Row { TextButton(editar) { Text("Editar") }; TextButton(excluir) { Text("Excluir") } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Pagina(titulo: String, voltar: () -> Unit, acao: @Composable (() -> Unit)? = null, conteudo: @Composable (PaddingValues) -> Unit) = Scaffold(topBar = { TopAppBar(title = { Text(titulo) }, navigationIcon = { TextButton(voltar) { Text("Voltar") } }, actions = { acao?.invoke() }) }, content = conteudo)

@Composable private fun DialogoUnidade(unidade: Unidade?, salvar: (Unidade) -> Unit, fechar: () -> Unit) {
    var numero by remember(unidade) { mutableStateOf(unidade?.numero.orEmpty()) }; var morador by remember(unidade) { mutableStateOf(unidade?.nomeMorador.orEmpty()) }; var peso by remember(unidade) { mutableStateOf(unidade?.peso?.toPlainString() ?: "1") }; var tentou by remember { mutableStateOf(false) }
    val pesoOk = peso.replace(',', '.').toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
    AlertDialog(onDismissRequest = fechar, title = { Text(if (unidade == null) "Adicionar unidade" else "Editar unidade") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Campo(numero, { numero = it }, "Número da unidade", tentou && numero.isBlank()); Campo(morador, { morador = it }, "Nome do morador"); Campo(peso, { peso = it }, "Peso", tentou && !pesoOk, KeyboardOptions(keyboardType = KeyboardType.Decimal))
    } }, confirmButton = { TextButton(onClick = { tentou = true; if (numero.isNotBlank() && pesoOk) salvar(Unidade(unidade?.id ?: 0, numero.trim(), morador.trim(), peso.replace(',', '.').toBigDecimal())) }) { Text("Salvar") } }, dismissButton = { TextButton(fechar) { Text("Cancelar") } })
}

@Composable private fun DialogoDespesa(despesa: Despesa?, salvar: (Despesa) -> Unit, fechar: () -> Unit) {
    var nome by remember(despesa) { mutableStateOf(despesa?.nome.orEmpty()) }; var tentou by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = fechar, title = { Text(if (despesa == null) "Adicionar despesa" else "Editar despesa") }, text = { Campo(nome, { nome = it }, "Nome da despesa", tentou && nome.isBlank()) }, confirmButton = { TextButton(onClick = { tentou = true; if (nome.isNotBlank()) salvar(Despesa(despesa?.id ?: 0, nome.trim(), despesa?.tipoRegra ?: TipoRegra.FIXO, despesa?.valorCentavos ?: 0)) }) { Text("Salvar") } }, dismissButton = { TextButton(fechar) { Text("Cancelar") } })
}

@Composable private fun Confirmar(mensagem: String, confirmar: () -> Unit, cancelar: () -> Unit) = AlertDialog(onDismissRequest = cancelar, title = { Text("Confirmar exclusão") }, text = { Text(mensagem) }, confirmButton = { TextButton(confirmar) { Text("Excluir") } }, dismissButton = { TextButton(cancelar) { Text("Cancelar") } })

@Composable private fun Campo(valor: String, mudar: (String) -> Unit, rotulo: String, erro: Boolean = false, teclado: KeyboardOptions = KeyboardOptions.Default, linhas: Int = 1) = OutlinedTextField(value = valor, onValueChange = mudar, label = { Text(rotulo) }, isError = erro, supportingText = if (erro) ({ Text("Campo obrigatório ou inválido") }) else null, keyboardOptions = teclado, minLines = linhas, modifier = Modifier.fillMaxWidth())
