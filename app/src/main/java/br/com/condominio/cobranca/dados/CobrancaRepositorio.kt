package br.com.condominio.cobranca.dados

import kotlinx.coroutines.flow.Flow

/** Reúne o acesso aos dados sem adicionar regras de tela. */
class CobrancaRepositorio(database: AppDatabase) {
    private val condominioDao = database.condominioDao()
    private val unidadeDao = database.unidadeDao()
    private val despesaDao = database.despesaDao()
    private val cobrancaDao = database.cobrancaDao()

    fun observarCondominio(): Flow<Condominio?> = condominioDao.observar()
    fun observarUnidades(): Flow<List<Unidade>> = unidadeDao.observarTodas()
    fun observarDespesas(): Flow<List<Despesa>> = despesaDao.observarTodas()
    fun observarCobrancas(): Flow<List<Cobranca>> = cobrancaDao.observarTodas()

    suspend fun salvarCondominio(condominio: Condominio) = condominioDao.salvar(condominio)
    suspend fun inserirUnidade(unidade: Unidade): Long = unidadeDao.inserir(unidade)
    suspend fun atualizarUnidade(unidade: Unidade) = unidadeDao.atualizar(unidade)
    suspend fun excluirUnidade(unidade: Unidade) = unidadeDao.excluir(unidade)
    suspend fun inserirDespesa(despesa: Despesa): Long = despesaDao.inserir(despesa)
    suspend fun atualizarDespesa(despesa: Despesa) = despesaDao.atualizar(despesa)
    suspend fun excluirDespesa(despesa: Despesa) = despesaDao.excluir(despesa)
    suspend fun salvarUnidadesDaDespesa(itens: List<DespesaUnidade>) = despesaDao.salvarUnidades(itens)
    suspend fun inserirCobranca(cobranca: Cobranca): Long = cobrancaDao.inserir(cobranca)
    suspend fun atualizarCobranca(cobranca: Cobranca) = cobrancaDao.atualizar(cobranca)
    suspend fun excluirCobranca(cobranca: Cobranca) = cobrancaDao.excluir(cobranca)
}
