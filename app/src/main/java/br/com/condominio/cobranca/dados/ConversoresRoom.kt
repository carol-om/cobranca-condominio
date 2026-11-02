package br.com.condominio.cobranca.dados

import androidx.room.TypeConverter
import java.math.BigDecimal

class ConversoresRoom {
    @TypeConverter
    fun deBigDecimal(valor: BigDecimal?): String? = valor?.toPlainString()

    @TypeConverter
    fun paraBigDecimal(valor: String?): BigDecimal? = valor?.let(::BigDecimal)

    @TypeConverter
    fun deTipoRegra(valor: TipoRegra?): String? = valor?.name

    @TypeConverter
    fun paraTipoRegra(valor: String?): TipoRegra? = valor?.let(TipoRegra::valueOf)

    @TypeConverter
    fun deStatusCobranca(valor: StatusCobranca?): String? = valor?.name

    @TypeConverter
    fun paraStatusCobranca(valor: String?): StatusCobranca? = valor?.let(StatusCobranca::valueOf)
}
