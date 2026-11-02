package br.com.condominio.cobranca.calculo

import br.com.condominio.cobranca.dados.TipoRegra
import java.math.BigDecimal
import java.math.BigInteger

data class UnidadeDaRegra(
    val unidadeId: Long,
    val isenta: Boolean = false,
    val valorCentavos: Long = 0,
    val peso: BigDecimal = BigDecimal.ONE,
)

/** Calcula valores em centavos mantendo a ordem recebida como ordem de cadastro. */
object CalculadoraDeRegras {
    fun calcular(
        tipoRegra: TipoRegra,
        valorCentavos: Long,
        unidades: List<UnidadeDaRegra>,
    ): Map<Long, Long> {
        require(valorCentavos >= 0) { "O valor em centavos não pode ser negativo." }

        return when (tipoRegra) {
            TipoRegra.FIXO -> unidades.associate { unidade ->
                unidade.unidadeId to if (unidade.isenta) 0L else valorCentavos
            }
            TipoRegra.POR_UNIDADE -> unidades.associate { unidade ->
                require(unidade.valorCentavos >= 0) { "O valor por unidade não pode ser negativo." }
                unidade.unidadeId to if (unidade.isenta) 0L else unidade.valorCentavos
            }
            TipoRegra.IGUALITARIO -> ratearIgualmente(valorCentavos, unidades)
            TipoRegra.PROPORCIONAL -> ratearProporcionalmente(valorCentavos, unidades)
        }
    }

    private fun ratearIgualmente(valorCentavos: Long, unidades: List<UnidadeDaRegra>): Map<Long, Long> {
        val ativas = unidades.filterNot(UnidadeDaRegra::isenta)
        if (ativas.isEmpty()) return unidades.associate { it.unidadeId to 0L }

        val valorBase = valorCentavos / ativas.size
        val sobra = (valorCentavos % ativas.size).toInt()
        var ordemDaSobra = 0

        return unidades.associate { unidade ->
            val valor = if (unidade.isenta) {
                0L
            } else {
                valorBase + if (ordemDaSobra++ < sobra) 1L else 0L
            }
            unidade.unidadeId to valor
        }
    }

    private fun ratearProporcionalmente(
        valorCentavos: Long,
        unidades: List<UnidadeDaRegra>,
    ): Map<Long, Long> {
        val ativas = unidades.filterNot(UnidadeDaRegra::isenta)
        if (ativas.isEmpty()) return unidades.associate { it.unidadeId to 0L }
        require(ativas.all { it.peso > BigDecimal.ZERO }) { "O peso deve ser maior que zero." }

        val escala = ativas.maxOf { it.peso.scale().coerceAtLeast(0) }
        val pesos = ativas.associateWith { unidade ->
            unidade.peso.movePointRight(escala).toBigIntegerExact()
        }
        val totalDosPesos = pesos.values.fold(BigInteger.ZERO, BigInteger::add)
        val total = BigInteger.valueOf(valorCentavos)
        val valoresBase = pesos.mapValues { (_, peso) -> total.multiply(peso).divide(totalDosPesos).toLong() }
        var sobra = valorCentavos - valoresBase.values.sum()

        return unidades.associate { unidade ->
            val valor = if (unidade.isenta) {
                0L
            } else {
                valoresBase.getValue(unidade) + if (sobra-- > 0) 1L else 0L
            }
            unidade.unidadeId to valor
        }
    }
}
