package br.com.condominio.cobranca.dados

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Condominio::class,
        Unidade::class,
        Despesa::class,
        DespesaUnidade::class,
        Cobranca::class,
        ItemCobranca::class,
        ItemCobrancaRegraUnidade::class,
        ItemCobrancaUnidade::class,
        CobrancaUnidade::class,
        CobrancaLinha::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(ConversoresRoom::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun condominioDao(): CondominioDao
    abstract fun unidadeDao(): UnidadeDao
    abstract fun despesaDao(): DespesaDao
    abstract fun cobrancaDao(): CobrancaDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun obter(contexto: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    AppDatabase::class.java,
                    "cobranca-condominio.db",
                ).fallbackToDestructiveMigration().build().also { instancia = it }
            }
    }
}
