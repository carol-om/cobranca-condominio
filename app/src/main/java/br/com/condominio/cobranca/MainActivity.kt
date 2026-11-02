package br.com.condominio.cobranca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.condominio.cobranca.dados.AppDatabase
import br.com.condominio.cobranca.dados.CobrancaRepositorio
import br.com.condominio.cobranca.ui.CobrancaApp
import br.com.condominio.cobranca.ui.CobrancaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val fabrica = viewModelFactory {
                initializer {
                    CobrancaViewModel(
                        CobrancaRepositorio(AppDatabase.obter(applicationContext)),
                    )
                }
            }
            val viewModel: CobrancaViewModel = viewModel(factory = fabrica)
            MaterialTheme {
                Surface {
                    CobrancaApp(viewModel = viewModel)
                }
            }
        }
    }
}

