package br.com.condominio.cobranca.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.condominio.cobranca.dados.*
import java.math.BigDecimal
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
}

@Composable fun CobrancaApp(viewModel: CobrancaViewModel) {
    val nav = rememberNavController()
    NavHost(nav, "inicio") {
        composable("inicio") { Inicio({ nav.navigate("condominio") }, { nav.navigate("unidades") }, { nav.navigate("despesas") }) }
        composable("condominio") { TelaCondominio(viewModel) { nav.popBackStack() } }
        composable("unidades") { TelaUnidades(viewModel) { nav.popBackStack() } }
        composable("despesas") { TelaDespesas(viewModel) { nav.popBackStack() } }
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

@Composable private fun TelaDespesas(vm: CobrancaViewModel, voltar: () -> Unit) {
    val lista by vm.despesas.collectAsStateWithLifecycle(); var editar by remember { mutableStateOf<Despesa?>(null) }; var novo by remember { mutableStateOf(false) }; var excluir by remember { mutableStateOf<Despesa?>(null) }
    Pagina("Despesas", voltar, { Button(onClick = { novo = true }) { Text("Adicionar") } }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(lista, { it.id }) { d -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(d.nome); Acoes({ editar = d }, { excluir = d }) } } } }
    }
    if (novo || editar != null) DialogoDespesa(editar, { vm.salvarDespesa(it); novo = false; editar = null }, { novo = false; editar = null })
    excluir?.let { d -> Confirmar("Excluir a despesa ${d.nome}?", { vm.excluirDespesa(d); excluir = null }, { excluir = null }) }
}

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
