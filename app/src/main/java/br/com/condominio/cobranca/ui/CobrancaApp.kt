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
}

@Composable fun CobrancaApp(viewModel: CobrancaViewModel) {
    val nav = rememberNavController()
    NavHost(nav, "inicio") {
        composable("inicio") { Inicio({ nav.navigate("condominio") }, { nav.navigate("unidades") }, { nav.navigate("despesas") }, { nav.navigate("nova") }, { nav.navigate("historico-unidades") }, { nav.navigate("fechadas") }) }
        composable("condominio") { TelaCondominio(viewModel) { nav.popBackStack() } }
        composable("unidades") { TelaUnidades(viewModel) { nav.popBackStack() } }
        composable("despesas") { TelaDespesas(viewModel, { nav.navigate("regra/$it") }) { nav.popBackStack() } }
        composable("regra/{despesaId}") { entrada ->
            val id = entrada.arguments?.getString("despesaId")?.toLongOrNull()
            TelaRegraDaDespesa(viewModel, id) { nav.popBackStack() }
        }
        composable("nova") { TelaNovaCobranca(viewModel) { nav.navigate("rascunho/$it") { popUpTo("inicio") } } }
        composable("rascunho/{cobrancaId}") { entrada -> TelaRascunho(viewModel, entrada.arguments?.getString("cobrancaId")?.toLongOrNull(), { nav.navigate("regra-item/$it") }) { nav.popBackStack() } }
        composable("regra-item/{itemId}") { entrada -> TelaRegraDoItem(viewModel, entrada.arguments?.getString("itemId")?.toLongOrNull()) { nav.popBackStack() } }
        composable("fechadas") { TelaCobrancasFechadas(viewModel, { nav.navigate("fechada/$it") }) { nav.popBackStack() } }
        composable("fechada/{cobrancaId}") { entrada -> TelaDetalheCobranca(viewModel, entrada.arguments?.getString("cobrancaId")?.toLongOrNull()) { nav.popBackStack() } }
        composable("historico-unidades") { TelaHistoricoUnidades(viewModel, { nav.navigate("historico-unidade/$it") }) { nav.popBackStack() } }
        composable("historico-unidade/{numero}") { entrada -> TelaHistoricoDaUnidade(viewModel, entrada.arguments?.getString("numero").orEmpty()) { nav.popBackStack() } }
    }
}

@Composable private fun Inicio(condominio: () -> Unit, unidades: () -> Unit, despesas: () -> Unit, nova: () -> Unit, historico: () -> Unit, fechadas: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text("Cobrança de condomínio")
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(condominio, Modifier.fillMaxWidth()) { Text("Condomínio") }
        Button(unidades, Modifier.fillMaxWidth()) { Text("Unidades") }
        Button(despesas, Modifier.fillMaxWidth()) { Text("Despesas") }
        Button(nova, Modifier.fillMaxWidth()) { Text("Nova cobrança") }
        Button(historico, Modifier.fillMaxWidth()) { Text("Histórico por unidade") }
        Button(fechadas, Modifier.fillMaxWidth()) { Text("Cobranças fechadas") }
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

@Composable private fun TelaNovaCobranca(vm: CobrancaViewModel, abrir: (Long) -> Unit) {
    val despesas by vm.despesas.collectAsStateWithLifecycle()
    val fechadas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    val rascunho by vm.rascunho.collectAsStateWithLifecycle()
    var mes by remember { mutableStateOf("") }; var ano by remember { mutableStateOf("") }; var vencimento by remember { mutableStateOf("") }
    var usarUltima by remember { mutableStateOf(fechadas.isNotEmpty()) }; var selecionadas by remember { mutableStateOf<Set<Long>>(emptySet()) }; var tentou by remember { mutableStateOf(false) }
    Pagina("Nova cobrança", {}) { p -> LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rascunho != null) {
            item { Text("Já existe um rascunho. Termine ou feche essa cobrança antes de criar outra.") }
            item { Button({ abrir(rascunho!!.id) }, Modifier.fillMaxWidth()) { Text("Abrir rascunho") } }
        } else {
            item { Campo(mes, { mes = it }, "Mês (1 a 12)", tentou && mes.toIntOrNull()?.let { it !in 1..12 } != false, KeyboardOptions(keyboardType = KeyboardType.Number)) }
            item { Campo(ano, { ano = it }, "Ano", tentou && (ano.toIntOrNull() ?: 0) < 2000, KeyboardOptions(keyboardType = KeyboardType.Number)) }
            item { Campo(vencimento, { vencimento = it }, "Vencimento (ex.: 10/01/2027)", tentou && vencimento.isBlank()) }
            item { Text("Itens da cobrança", style = MaterialTheme.typography.titleMedium) }
            if (fechadas.isNotEmpty()) item { Row(Modifier.clickable { usarUltima = true }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(usarUltima, { usarUltima = true }); Text("Partir da última cobrança fechada") } }
            item { Row(Modifier.clickable { usarUltima = false }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(!usarUltima, { usarUltima = false }); Text("Marcar despesas cadastradas") } }
            if (!usarUltima) items(despesas, { it.id }) { d -> Row(Modifier.fillMaxWidth().clickable { selecionadas = if (d.id in selecionadas) selecionadas - d.id else selecionadas + d.id }.padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(d.id in selecionadas, null); Text(d.nome) } }
            item { Button(onClick = { tentou = true; val m = mes.toIntOrNull(); val a = ano.toIntOrNull(); if (m != null && m in 1..12 && a != null && a >= 2000 && vencimento.isNotBlank() && (usarUltima || selecionadas.isNotEmpty())) vm.criarRascunho(m, a, vencimento.trim(), usarUltima, despesas.filter { it.id in selecionadas }, abrir) }, modifier = Modifier.fillMaxWidth()) { Text("Criar rascunho") } }
        }
    } }
}

private fun calcularItem(item: ItemCobranca, unidades: List<Unidade>, regras: List<ItemCobrancaRegraUnidade>) = CalculadoraDeRegras.calcular(item.tipoRegra, item.valorCentavos, unidades.map { u -> regras.firstOrNull { it.unidadeId == u.id }.let { r -> UnidadeDaRegra(u.id, r?.isenta ?: false, r?.valorCentavos ?: 0, u.peso) } })

@Composable private fun TelaRascunho(vm: CobrancaViewModel, cobrancaId: Long?, editar: (Long) -> Unit, voltar: () -> Unit) {
    if (cobrancaId == null) return
    val itens by vm.itensDaCobranca(cobrancaId).collectAsStateWithLifecycle(emptyList())
    val unidades by vm.unidades.collectAsStateWithLifecycle()
    val calculos = itens.map { item ->
        val regras by vm.regraDoItem(item.id).collectAsStateWithLifecycle(emptyList())
        item to calcularItem(item, unidades, regras)
    }
    var confirmar by remember { mutableStateOf(false) }
    Pagina("Conferência da cobrança", voltar) { p -> LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Conferência por unidade", style = MaterialTheme.typography.titleMedium) }
        items(unidades, { it.id }) { unidade ->
            val itensDaUnidade = calculos.mapNotNull { (item, valores) -> valores[unidade.id]?.takeIf { it > 0 }?.let { item.nome to it } }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Unidade ${unidade.numero} — ${unidade.nomeMorador}", style = MaterialTheme.typography.titleMedium); itensDaUnidade.forEach { (nome, valor) -> Text("$nome: ${valorFormatado(valor)}") }; Text("Total: ${valorFormatado(itensDaUnidade.sumOf { it.second })}") } }
        }
        item { Text("Itens do mês (toque para editar)", style = MaterialTheme.typography.titleMedium) }
        items(calculos, { it.first.id }) { (item, _) -> Card(Modifier.fillMaxWidth().clickable { editar(item.id) }) { Text(item.nome, Modifier.padding(12.dp)) } }
        item { Text("Total geral: ${valorFormatado(calculos.sumOf { it.second.values.sum() })}", style = MaterialTheme.typography.titleMedium) }
        item { Button({ confirmar = true }, Modifier.fillMaxWidth(), enabled = itens.isNotEmpty() && unidades.isNotEmpty()) { Text("Fechar cobrança") } }
    } }
    // A lista de itens abaixo evita uma tela adicional e mantém a edição explícita.
    if (confirmar) ConfirmarFechamento({ vm.fecharCobranca(cobrancaId) { voltar() }; confirmar = false }, { confirmar = false })
}

@Composable private fun ConfirmarFechamento(confirmar: () -> Unit, cancelar: () -> Unit) = AlertDialog(onDismissRequest = cancelar, title = { Text("Fechar cobrança?") }, text = { Text("Depois de fechada, esta cobrança não poderá ser editada.") }, confirmButton = { TextButton(confirmar) { Text("Fechar cobrança") } }, dismissButton = { TextButton(cancelar) { Text("Cancelar") } })

@Composable private fun TelaRegraDoItem(vm: CobrancaViewModel, itemId: Long?, voltar: () -> Unit) {
    if (itemId == null) return
    val item by vm.item(itemId).collectAsStateWithLifecycle(null)
    val unidades by vm.unidades.collectAsStateWithLifecycle(); val salvas by vm.regraDoItem(itemId).collectAsStateWithLifecycle(emptyList())
    item ?: return
    var tipo by remember(itemId) { mutableStateOf(item!!.tipoRegra) }; var valor by remember(itemId) { mutableStateOf(centavosParaCampo(item!!.valorCentavos)) }; var valores by remember(itemId) { mutableStateOf<Map<Long, String>>(emptyMap()) }; var isentas by remember(itemId) { mutableStateOf<Set<Long>>(emptySet()) }; var tentou by remember { mutableStateOf(false) }
    LaunchedEffect(salvas) { isentas = salvas.filter { it.isenta }.map { it.unidadeId }.toSet(); valores = salvas.associate { it.unidadeId to centavosParaCampo(it.valorCentavos) } }
    val centavos = valor.paraCentavos(); val incompleto = tipo == TipoRegra.POR_UNIDADE && unidades.any { it.id !in isentas && valores[it.id].orEmpty().paraCentavos() == null }
    Pagina("Regra do item", voltar) { p -> LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(item!!.nome, style = MaterialTheme.typography.titleMedium) }; item { TipoRegra.values().forEach { t -> Row(Modifier.clickable { tipo = t }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(tipo == t, { tipo = t }); Text(nomeDoTipo(t)) } } }
        if (tipo != TipoRegra.POR_UNIDADE) item { Campo(valor, { valor = it }, if (tipo == TipoRegra.FIXO) "Valor por unidade" else "Valor total a ratear", tentou && centavos == null, KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
        items(unidades, { it.id }) { u -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text("Unidade ${u.numero} — ${u.nomeMorador}"); Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(u.id in isentas, { b -> isentas = if (b) isentas + u.id else isentas - u.id }); Text("Isenta") }; if (tipo == TipoRegra.POR_UNIDADE) Campo(valores[u.id].orEmpty(), { v -> valores = valores + (u.id to v) }, "Valor desta unidade", tentou && u.id !in isentas && valores[u.id].orEmpty().paraCentavos() == null, KeyboardOptions(keyboardType = KeyboardType.Decimal)); if (tipo == TipoRegra.PROPORCIONAL) Text("Peso: ${u.peso}") } } }
        item { Button({ tentou = true; if (!incompleto && (tipo == TipoRegra.POR_UNIDADE || centavos != null)) { val regras = unidades.map { u -> ItemCobrancaRegraUnidade(itemId, u.id, u.id in isentas, valores[u.id].orEmpty().paraCentavos() ?: 0) }; vm.salvarRegraDoItem(item!!.copy(tipoRegra = tipo, valorCentavos = centavos ?: 0), regras, voltar) } }, Modifier.fillMaxWidth()) { Text("Salvar regra") } }
    } }
}

@Composable private fun TelaCobrancasFechadas(vm: CobrancaViewModel, abrir: (Long) -> Unit, voltar: () -> Unit) {
    val cobrancas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    Pagina("Cobranças fechadas", voltar) { p -> LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cobrancas, { it.id }) { c -> Card(Modifier.fillMaxWidth().clickable { abrir(c.id) }) { Column(Modifier.padding(14.dp)) { Text("${c.mes.toString().padStart(2, '0')}/${c.ano}", style = MaterialTheme.typography.titleMedium); Text("Vencimento: ${c.vencimento}") } } }
        if (cobrancas.isEmpty()) item { Text("Nenhuma cobrança fechada.") }
    } }
}

@Composable private fun TelaDetalheCobranca(vm: CobrancaViewModel, cobrancaId: Long?, voltar: () -> Unit) {
    var unidades by remember { mutableStateOf<List<CobrancaUnidade>>(emptyList()) }; var linhas by remember { mutableStateOf<Map<Long, List<CobrancaLinha>>>(emptyMap()) }
    LaunchedEffect(cobrancaId) { cobrancaId?.let { vm.detalhesDaCobranca(it) { u, l -> unidades = u; linhas = l } } }
    Pagina("Unidades da cobrança", voltar) { p -> LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(unidades, { it.id }) { u -> val total = linhas[u.id].orEmpty().sumOf { it.valorCentavos }; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("Unidade ${u.numeroUnidade} — ${u.nomeMorador}"); Text("Total: ${valorFormatado(total)}", style = MaterialTheme.typography.titleMedium) } } }
        if (unidades.isEmpty()) item { Text("Carregando unidades...") }
    } }
}

@Composable private fun TelaHistoricoUnidades(vm: CobrancaViewModel, abrir: (String) -> Unit, voltar: () -> Unit) {
    val unidades by vm.unidades.collectAsStateWithLifecycle()
    Pagina("Histórico por unidade", voltar) { p -> LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(unidades, { it.id }) { u -> Card(Modifier.fillMaxWidth().clickable { abrir(u.numero) }) { Text("Unidade ${u.numero} — ${u.nomeMorador}", Modifier.padding(14.dp)) } }
        if (unidades.isEmpty()) item { Text("Nenhuma unidade cadastrada.") }
    } }
}

@Composable private fun TelaHistoricoDaUnidade(vm: CobrancaViewModel, numero: String, voltar: () -> Unit) {
    val cobrancas by vm.cobrancasFechadas.collectAsStateWithLifecycle()
    var resultado by remember(numero, cobrancas) { mutableStateOf<List<Pair<Cobranca, Pair<CobrancaUnidade, List<CobrancaLinha>>>>(emptyList()) }
    LaunchedEffect(numero, cobrancas) {
        vm.historicoDaUnidade(numero, cobrancas) { resultado = it }
    }
    Pagina("Histórico da unidade $numero", voltar) { p -> LazyColumn(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(resultado, { it.first.id }) { (c, dados) -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("${c.mes.toString().padStart(2, '0')}/${c.ano} — vencimento ${c.vencimento}", style = MaterialTheme.typography.titleMedium); dados.second.forEach { Text("${it.descricao}: ${valorFormatado(it.valorCentavos)}") }; Text("Total: ${valorFormatado(dados.second.sumOf { it.valorCentavos })}") } } }
        if (resultado.isEmpty()) item { Text("Não há cobranças fechadas para esta unidade.") }
    } }
}

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
