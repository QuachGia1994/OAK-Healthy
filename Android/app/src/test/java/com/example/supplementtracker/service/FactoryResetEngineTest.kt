package com.example.supplementtracker.service

import android.content.Context
import androidx.room.Room
import com.example.supplementtracker.data.local.SupplementDatabase
import com.example.supplementtracker.data.repository.SupplementRepositoryImpl
import com.example.supplementtracker.domain.model.ClientProfile
import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.UserSupplement
import com.example.supplementtracker.domain.repository.IntakeRecord
import com.example.supplementtracker.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FactoryResetEngineTest {
    private lateinit var database: SupplementDatabase
    private lateinit var repository: SupplementRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SupplementDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SupplementRepositoryImpl(database.supplementDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resetRunsSideEffectsInSafeOrderAndDeletesCascadeData() = runBlocking {
        val clientId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val supplementId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        repository.saveClient(ClientProfile(id = clientId, name = "Reset Me", avatarColorArgb = 0))
        repository.saveSupplement(
            UserSupplement(
                id = supplementId,
                clientId = clientId,
                name = "Creatine",
                startDate = LocalDate.of(2026, 1, 1),
                cycleConfig = CycleConfig(daysOn = 1, daysOff = 0, isContinuous = true),
                dailyDose = "5 g",
                intakeTime = "08:00"
            )
        )
        repository.insertIntakeRecord(
            IntakeRecord(
                id = "dose-1",
                supplementId = supplementId.toString(),
                date = 1_700_000_000_000L,
                status = "Taken"
            )
        )
        val events = mutableListOf<String>()
        val engine = FactoryResetEngine(
            repository = repository,
            clearNotifications = { events += "notifications" },
            disableAutoSync = { events += "autosync" },
            clearPreferences = { events += "prefs" },
            clearCryptoMaterial = { events += "crypto" },
            clearActiveClient = { events += "active" }
        )

        assertTrue(engine.reset().isSuccess)
        assertEquals(
            listOf("notifications", "autosync", "prefs", "crypto", "active"),
            events
        )
        assertTrue(repository.observeClients().first().isEmpty())
        assertTrue(repository.getAllSupplementsForSync(clientId.toString()).isEmpty())
        assertTrue(repository.getAllRecordsForSync(clientId.toString()).isEmpty())
    }
}
