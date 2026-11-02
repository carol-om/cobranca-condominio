package br.com.condominio.cobranca.dados

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

enum class TipoRegra {
    FIXO,
    POR_UNIDADE,
    IGUALITARIO,
    PROPORCIONAL,
}

enum class StatusCobranca {
    RASCUNHO,
    FECHADA,
}

/** Há somente um condomínio salvo, identificado sempre pelo id 1. */
@Entity(tableName = "condominio")
data class Condominio(
    @PrimaryKey val id: Int = ID_UNICO,
    val nome: String? = null,
    val nomeSindica: String = "",
    val chavePix: String = "",
    val instrucoesPagamento: String = "",
) {
    companion object {
        const val ID_UNICO = 1
    }
}

@Entity(tableName = "unidade")
data class Unidade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numero: String,
    val nomeMorador: String,
    val peso: BigDecimal = BigDecimal.ONE,
)

@Entity(tableName = "despesa")
data class Despesa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val tipoRegra: TipoRegra,
    val valorCentavos: Long,
)

@Entity(
    tableName = "despesa_unidade",
    primaryKeys = ["despesaId", "unidadeId"],
    foreignKeys = [
        ForeignKey(
            entity = Despesa::class,
            parentColumns = ["id"],
            childColumns = ["despesaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Unidade::class,
            parentColumns = ["id"],
            childColumns = ["unidadeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("unidadeId")],
)
data class DespesaUnidade(
    val despesaId: Long,
    val unidadeId: Long,
    val isenta: Boolean = false,
    val valorCentavos: Long = 0,
)

@Entity(tableName = "cobranca")
data class Cobranca(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mes: Int,
    val ano: Int,
    val vencimento: String,
    val status: StatusCobranca = StatusCobranca.RASCUNHO,
    val nomeCondominio: String? = null,
    val nomeSindica: String? = null,
    val chavePix: String? = null,
    val instrucoesPagamento: String? = null,
)

/** Cópia editável de uma despesa durante a preparação da cobrança mensal. */
@Entity(
    tableName = "item_cobranca",
    foreignKeys = [
        ForeignKey(
            entity = Cobranca::class,
            parentColumns = ["id"],
            childColumns = ["cobrancaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cobrancaId")],
)
data class ItemCobranca(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cobrancaId: Long,
    val nome: String,
    val tipoRegra: TipoRegra,
    val valorCentavos: Long,
)

@Entity(
    tableName = "cobranca_unidade",
    foreignKeys = [
        ForeignKey(
            entity = Cobranca::class,
            parentColumns = ["id"],
            childColumns = ["cobrancaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cobrancaId")],
)
data class CobrancaUnidade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cobrancaId: Long,
    val numeroUnidade: String,
    val nomeMorador: String,
)

@Entity(
    tableName = "item_cobranca_unidade",
    primaryKeys = ["itemCobrancaId", "cobrancaUnidadeId"],
    foreignKeys = [
        ForeignKey(
            entity = ItemCobranca::class,
            parentColumns = ["id"],
            childColumns = ["itemCobrancaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CobrancaUnidade::class,
            parentColumns = ["id"],
            childColumns = ["cobrancaUnidadeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cobrancaUnidadeId")],
)
data class ItemCobrancaUnidade(
    val itemCobrancaId: Long,
    val cobrancaUnidadeId: Long,
    val isenta: Boolean = false,
    val valorCentavos: Long = 0,
)

@Entity(
    tableName = "cobranca_linha",
    foreignKeys = [
        ForeignKey(
            entity = CobrancaUnidade::class,
            parentColumns = ["id"],
            childColumns = ["cobrancaUnidadeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cobrancaUnidadeId")],
)
data class CobrancaLinha(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cobrancaUnidadeId: Long,
    val descricao: String,
    val valorCentavos: Long,
)
