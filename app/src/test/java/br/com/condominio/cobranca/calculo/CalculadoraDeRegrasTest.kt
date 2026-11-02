package br.com.condominio.cobranca.calculo

import br.com.condominio.cobranca.dados.TipoRegra
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculadoraDeRegrasTest {
    @Test
    fun `agua por unidade respeita isencao e soma 440 reais`() {
        val resultado = CalculadoraDeRegras.calcular(
            tipoRegra = TipoRegra.POR_UNIDADE,
            valorCentavos = 0,
            unidades = listOf(
                UnidadeDaRegra(11, valorCentavos = 7000),
                UnidadeDaRegra(12, valorCentavos = 7000),
                UnidadeDaRegra(21, valorCentavos = 7000),
                UnidadeDaRegra(22, valorCentavos = 7000),
                UnidadeDaRegra(31, valorCentavos = 8000),
                UnidadeDaRegra(32, valorCentavos = 8000),
                UnidadeDaRegra(99, isenta = true, valorCentavos = 9000),
            ),
        )

        assertEquals(7000L, resultado.getValue(11))
        assertEquals(8000L, resultado.getValue(31))
        assertEquals(0L, resultado.getValue(99))
        assertEquals(44000L, resultado.values.sum())
    }

    @Test
    fun `rateio igualitario entrega a sobra as primeiras unidades`() {
        val resultado = CalculadoraDeRegras.calcular(
            TipoRegra.IGUALITARIO,
            10000,
            listOf(UnidadeDaRegra(1), UnidadeDaRegra(2), UnidadeDaRegra(3)),
        )

        assertEquals(mapOf(1L to 3334L, 2L to 3333L, 3L to 3333L), resultado)
    }

    @Test
    fun `rateio igualitario ignora unidade isenta`() {
        val resultado = CalculadoraDeRegras.calcular(
            TipoRegra.IGUALITARIO,
            10000,
            listOf(UnidadeDaRegra(1), UnidadeDaRegra(2, isenta = true), UnidadeDaRegra(3)),
        )

        assertEquals(mapOf(1L to 5000L, 2L to 0L, 3L to 5000L), resultado)
    }

    @Test
    fun `rateio proporcional com pesos diferentes tem soma exata`() {
        val resultado = CalculadoraDeRegras.calcular(
            TipoRegra.PROPORCIONAL,
            101,
            listOf(
                UnidadeDaRegra(1, peso = BigDecimal("1")),
                UnidadeDaRegra(2, peso = BigDecimal("2")),
                UnidadeDaRegra(3, peso = BigDecimal("3")),
            ),
        )

        assertEquals(mapOf(1L to 17L, 2L to 34L, 3L to 50L), resultado)
        assertEquals(101L, resultado.values.sum())
    }

    @Test
    fun `valor fixo por unidade respeita isencao`() {
        val resultado = CalculadoraDeRegras.calcular(
            TipoRegra.FIXO,
            5000,
            listOf(UnidadeDaRegra(1), UnidadeDaRegra(2, isenta = true), UnidadeDaRegra(3)),
        )

        assertEquals(mapOf(1L to 5000L, 2L to 0L, 3L to 5000L), resultado)
    }
}
