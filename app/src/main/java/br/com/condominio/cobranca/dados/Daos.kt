package br.com.condominio.cobranca.dados

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CondominioDao {
    @Query("SELECT * FROM condominio WHERE id = :id")
    fun observar(id: Int = Condominio.ID_UNICO): Flow<Condominio?>

    @Query("SELECT * FROM condominio WHERE id = :id")
    suspend fun buscar(id: Int = Condominio.ID_UNICO): Condominio?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(condominio: Condominio)
}

@Dao
interface UnidadeDao {
    @Query("SELECT * FROM unidade ORDER BY id")
    fun observarTodas(): Flow<List<Unidade>>

    @Query("SELECT * FROM unidade ORDER BY id")
    suspend fun listarTodas(): List<Unidade>

    @Insert
    suspend fun inserir(unidade: Unidade): Long

    @Update
    suspend fun atualizar(unidade: Unidade)

    @Delete
    suspend fun excluir(unidade: Unidade)
}

@Dao
interface DespesaDao {
    @Query("SELECT * FROM despesa ORDER BY id")
    fun observarTodas(): Flow<List<Despesa>>

    @Insert
    suspend fun inserir(despesa: Despesa): Long

    @Update
    suspend fun atualizar(despesa: Despesa)

    @Delete
    suspend fun excluir(despesa: Despesa)

    @Query("SELECT * FROM despesa_unidade WHERE despesaId = :despesaId ORDER BY unidadeId")
    suspend fun listarUnidades(despesaId: Long): List<DespesaUnidade>

    @Query("SELECT * FROM despesa_unidade WHERE despesaId = :despesaId ORDER BY unidadeId")
    fun observarUnidades(despesaId: Long): Flow<List<DespesaUnidade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarUnidades(itens: List<DespesaUnidade>)

    @Query("DELETE FROM despesa_unidade WHERE despesaId = :despesaId")
    suspend fun excluirUnidades(despesaId: Long)
}

@Dao
interface CobrancaDao {
    @Query("SELECT * FROM cobranca ORDER BY ano DESC, mes DESC, id DESC")
    fun observarTodas(): Flow<List<Cobranca>>

    @Query("SELECT * FROM cobranca WHERE status = 'RASCUNHO' ORDER BY id DESC LIMIT 1")
    fun observarRascunho(): Flow<Cobranca?>

    @Query("SELECT * FROM cobranca WHERE status = 'RASCUNHO' ORDER BY id DESC LIMIT 1")
    suspend fun buscarRascunho(): Cobranca?

    @Query("SELECT * FROM cobranca WHERE status = 'FECHADA' ORDER BY ano DESC, mes DESC, id DESC")
    fun observarFechadas(): Flow<List<Cobranca>>

    @Query("DELETE FROM cobranca WHERE status = 'FECHADA'")
    suspend fun excluirFechadas()

    @Query("SELECT * FROM cobranca WHERE id = :id")
    suspend fun buscar(id: Long): Cobranca?

    @Query("SELECT * FROM cobranca WHERE status = 'FECHADA' ORDER BY ano DESC, mes DESC, id DESC LIMIT 1")
    suspend fun buscarUltimaFechada(): Cobranca?

    @Insert
    suspend fun inserir(cobranca: Cobranca): Long

    @Update
    suspend fun atualizar(cobranca: Cobranca)

    @Delete
    suspend fun excluir(cobranca: Cobranca)

    @Query("SELECT * FROM item_cobranca WHERE cobrancaId = :cobrancaId ORDER BY id")
    suspend fun listarItens(cobrancaId: Long): List<ItemCobranca>

    @Query("SELECT * FROM item_cobranca WHERE cobrancaId = :cobrancaId ORDER BY id")
    fun observarItens(cobrancaId: Long): Flow<List<ItemCobranca>>

    @Query("SELECT * FROM item_cobranca WHERE id = :id")
    fun observarItem(id: Long): Flow<ItemCobranca?>

    @Insert
    suspend fun inserirItem(item: ItemCobranca): Long

    @Update
    suspend fun atualizarItem(item: ItemCobranca)

    @Delete
    suspend fun excluirItem(item: ItemCobranca)

    @Query("SELECT * FROM item_cobranca_regra_unidade WHERE itemCobrancaId = :itemCobrancaId ORDER BY unidadeId")
    suspend fun listarRegraUnidades(itemCobrancaId: Long): List<ItemCobrancaRegraUnidade>

    @Query("SELECT * FROM item_cobranca_regra_unidade WHERE itemCobrancaId = :itemCobrancaId ORDER BY unidadeId")
    fun observarRegraUnidades(itemCobrancaId: Long): Flow<List<ItemCobrancaRegraUnidade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarRegraUnidades(itens: List<ItemCobrancaRegraUnidade>)

    @Query("DELETE FROM item_cobranca_regra_unidade WHERE itemCobrancaId = :itemCobrancaId")
    suspend fun excluirRegraUnidades(itemCobrancaId: Long)

    @Query("SELECT * FROM item_cobranca_unidade WHERE itemCobrancaId = :itemCobrancaId ORDER BY cobrancaUnidadeId")
    suspend fun listarUnidadesDoItem(itemCobrancaId: Long): List<ItemCobrancaUnidade>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarUnidadesDoItem(itens: List<ItemCobrancaUnidade>)

    @Query("DELETE FROM item_cobranca_unidade WHERE itemCobrancaId = :itemCobrancaId")
    suspend fun excluirUnidadesDoItem(itemCobrancaId: Long)

    @Query("SELECT * FROM cobranca_unidade WHERE cobrancaId = :cobrancaId ORDER BY id")
    suspend fun listarUnidades(cobrancaId: Long): List<CobrancaUnidade>

    @Insert
    suspend fun inserirUnidades(unidades: List<CobrancaUnidade>): List<Long>

    @Query("SELECT * FROM cobranca_linha WHERE cobrancaUnidadeId = :cobrancaUnidadeId ORDER BY id")
    suspend fun listarLinhas(cobrancaUnidadeId: Long): List<CobrancaLinha>

    @Insert
    suspend fun inserirLinhas(linhas: List<CobrancaLinha>): List<Long>
}
