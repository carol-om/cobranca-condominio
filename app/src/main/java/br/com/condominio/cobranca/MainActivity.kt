package br.com.condominio.cobranca

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.condominio.cobranca.dados.AppDatabase
import br.com.condominio.cobranca.dados.CobrancaRepositorio
import br.com.condominio.cobranca.ui.CobrancaApp
import br.com.condominio.cobranca.ui.CobrancaViewModel
import br.com.condominio.cobranca.ui.TemaCobranca

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barra de status com ícones claros (o topo do app é verde) e barra de navegação com ícones escuros.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val fabrica = viewModelFactory {
                initializer {
                    CobrancaViewModel(
                        CobrancaRepositorio(AppDatabase.obter(applicationContext)),
                    )
                }
            }
            val viewModel: CobrancaViewModel = viewModel(factory = fabrica)
            TemaCobranca {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CobrancaApp(viewModel = viewModel)
                }
            }
        }
    }
}
