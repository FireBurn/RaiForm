package uk.co.fireburn.raiform.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.co.fireburn.raiform.data.source.local.RaiFormDatabase
import uk.co.fireburn.raiform.data.source.local.dao.ClientDao
import uk.co.fireburn.raiform.data.source.local.dao.HistoryDao
import uk.co.fireburn.raiform.data.source.local.dao.SessionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): RaiFormDatabase {
        return Room.databaseBuilder(
            context,
            RaiFormDatabase::class.java,
            RaiFormDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideClientDao(database: RaiFormDatabase): ClientDao {
        return database.clientDao()
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: RaiFormDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: RaiFormDatabase): HistoryDao {
        return database.historyDao()
    }
}
