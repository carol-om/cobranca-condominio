package br.com.condominio.cobranca.dados

import androidx.room.withTransaction
import br.com.condominio.cobranca.calculo.CalculadoraDeRegras
import br.com.condominio.cobranca.calculo.UnidadeDaRegra
import kotlinx.coroutines.flow.Flow

/** Reúne o acesso aos dados sem adicionar regras de tela. */
class CobrancaRepositorio(private val database: AppDatabase) {
    private val condominioDao = database.condominioDao()
    private val unidadeDao = database.unidadeDao()
    private val despesaDao = database.despesaDao()
    private val cobrancaDao = database.cobrancaDao()

    fun observarCondominio(): Flow<Condominio?> = condominioDao.observar()
    fun observarUnidades(): Flow<List<Unidade>> = unidadeDao.observarTodas()
    fun observarDespesas(): Flow<List<Despesa>> = despesaDao.observarTodas()
    fun observarUnidadesDaDespesa(despesaId: Long): Flow<List<DespesaUnidade>> = despesaDao.observarUnidades(despesaId)
    fun observarCobrancas(): Flow<List<Cobranca>> = cobrancaDao.observarTodas()
    fun observarRascunho(): Flow<Cobranca?> = cobrancaDao.observarRascunho()
    fun observarFechadas(): Flow<List<Cobranca>> = cobrancaDao.observarFechadas()
    fun observarItensDaCobranca(id: Long): Flow<List<ItemCobranca>> = cobrancaDao.observarItens(id)
    fun observarItem(id: Long): Flow<ItemCobranca?> = cobrancaDao.observarItem(id)
    fun observarRegraDoItem(id: Long): Flow<List<ItemCobrancaRegraUnidade>> = cobrancaDao.observarRegraUnidades(id)

    suspend fun salvarCondominio(condominio: Condominio) = condominioDao.salvar(condominio)
    suspend fun inserirUnidade(unidade: Unidade): Long = unidadeDao.inserir(unidade)
    suspend fun atualizarUnidade(unidade: Unidade) = unidadeDao.atualizar(unidade)
    suspend fun excluirUnidade(unidade: Unidade) = unidadeDao.excluir(unidade)
    suspend fun inserirDespesa(despesa: Despesa): Long = despesaDao.inserir(despesa)
    suspend fun atualizarDespesa(despesa: Despesa) = despesaDao.atualizar(despesa)
    suspend fun excluirDespesa(despesa: Despesa) = despesaDao.excluir(despesa)
    suspend fun salvarUnidadesDaDespesa(itens: List<DespesaUnidade>) = despesaDao.salvarUnidades(itens)
    suspend fun salvarRegraDaDespesa(despesa: Despesa, itens: List<DespesaUnidade>) = database.withTransaction {
        despesaDao.atualizar(despesa)
        despesaDao.excluirUnidades(despesa.id)
        if (itens.isNotEmpty()) despesaDao.salvarUnidades(itens)
    }
    suspend fun inserirCobranca(cobranca: Cobranca): Long = cobrancaDao.inserir(cobranca)
    suspend fun atualizarCobranca(cobranca: Cobranca) = cobrancaDao.atualizar(cobranca)
    suspend fun excluirCobranca(cobranca: Cobranca) = cobrancaDao.excluir(cobranca)

    suspend fun criarRascunho(mes: Int, ano: Int, vencimento: String, usarUltima: Boolean, despesas: List<Despesa>): Long = database.withTransaction {
        check(cobrancaDao.buscarRascunho() == null) { "Já existe um rascunho." }
        val id = cobrancaDao.inserir(Cobranca(mes = mes, ano = ano, vencimento = vencimento))
        val origem = if (usarUltima) cobrancaDao.buscarUltimaFechada()?.let { cobrancaDao.listarItens(it.id) } ?: emptyList() else emptyList()
        val itens: List<Any> = if (origem.isNotEmpty()) origem else despesas
        itens.forEach { origemItem ->
            val item = when (origemItem) {
                is ItemCobranca -> ItemCobranca(cobrancaId = id, nome = origemItem.nome, tipoRegra = origemItem.tipoRegra, valorCentavos = origemItem.valorCentavos)
                is Despesa -> ItemCobranca(cobrancaId = id, nome = origemItem.nome, tipoRegra = origemItem.tipoRegra, valorCentavos = origemItem.valorCentavos)
                else -> error("Item inválido")
            }
            val itemId = cobrancaDao.inserirItem(item)
            val regras = when (origemItem) {
                is ItemCobranca -> cobrancaDao.listarRegraUnidades(origemItem.id)
                is Despesa -> despesaDao.listarUnidades(origemItem.id).map { ItemCobrancaRegraUnidade(itemId, it.unidadeId, it.isenta, it.valorCentavos) }
                else -> emptyList()
            }
            if (origemItem is ItemCobranca && regras.isNotEmpty()) cobrancaDao.salvarRegraUnidades(regras.map { it.copy(itemCobrancaId = itemId) })
            else if (regras.isNotEmpty()) cobrancaDao.salvarRegraUnidades(regras)
        }
        id
    }

    suspend fun salvarRegraDoItem(item: ItemCobranca, regras: List<ItemCobrancaRegraUnidade>) = database.withTransaction {
        cobrancaDao.atualizarItem(item)
        cobrancaDao.excluirRegraUnidades(item.id)
        if (regras.isNotEmpty()) cobrancaDao.salvarRegraUnidades(regras)
    }

    suspend fun fecharCobranca(cobrancaId: Long) = database.withTransaction {
        val cobranca = requireNotNull(cobrancaDao.buscar(cobrancaId))
        check(cobranca.status == StatusCobranca.RASCUNHO)
        val condominio = condominioDao.buscar()
        val unidades = unidadeDao.listarTodas()
        val itens = cobrancaDao.listarItens(cobrancaId)
        val unidadesFechadas = unidades.map { CobrancaUnidade(cobrancaId = cobrancaId, numeroUnidade = it.numero, nomeMorador = it.nomeMorador) }
        val ids = cobrancaDao.inserirUnidades(unidadesFechadas)
        unidades.zip(ids).forEach { (unidade, unidadeFechadaId) ->
            val linhas = itens.mapNotNull { item ->
                val regras = cobrancaDao.listarRegraUnidades(item.id).associateBy { it.unidadeId }
                val regra = regras[unidade.id]
                val valores = CalculadoraDeRegras.calcular(item.tipoRegra, item.valorCentavos, unidades.map { u ->
                    val r = regras[u.id]
                    UnidadeDaRegra(u.id, r?.isenta ?: false, r?.valorCentavos ?: 0, u.peso)
                })
                valores[unidade.id]?.takeIf { it > 0 }?.let { CobrancaLinha(cobrancaUnidadeId = unidadeFechadaId, descricao = item.nome, valorCentavos = it) }
            }
            if (linhas.isNotEmpty()) cobrancaDao.inserirLinhas(linhas)
        }
        cobrancaDao.atualizar(cobranca.copy(status = StatusCobranca.FECHADA, nomeCondominio = condominio?.nome, nomeSindica = condominio?.nomeSindica, chavePix = condominio?.chavePix, instrucoesPagamento = condominio?.instrucoesPagamento))
    }

    suspend fun listarUnidadesFechadas(cobrancaId: Long) = cobrancaDao.listarUnidades(cobrancaId)
    suspend fun listarLinhasDaUnidade(cobrancaUnidadeId: Long) = cobrancaDao.listarLinhas(cobrancaUnidadeId)
}
