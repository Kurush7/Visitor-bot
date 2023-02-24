package ru.kudryavtsev

import com.qoollo.common.model.FilePolicy
import com.qoollo.logback.LogbackLogger
import com.qoollo.logger.QoolloLogger
import com.qoollo.logger.logi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.launchIn
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.startKoin
import ru.kudryavtsev.datasource.local.entity.Students
import ru.kudryavtsev.datasource.local.entity.Visits
import ru.kudryavtsev.domain.BotController
import ru.kudryavtsev.domain.di.domainModule
import ru.kudryavtsev.domain.di.remoteModule
import ru.kudryavtsev.domain.model.AppContext
import java.sql.Connection

suspend fun main() {
    val currentContext = AppContext.getEnvironment()
    initializeLogger(currentContext)
    logi(tag = "BotApp") {
        "selected context: ${currentContext.javaClass.simpleName}\n" +
                "volume path: ${currentContext.volumePath}\n" +
                "db path: ${currentContext.dbPath}"
    }
    initializeDb(currentContext)
    startKoin {
        modules(domainModule, remoteModule)
    }
    val scope = CoroutineScope(Dispatchers.Default)
    val botController = BotController()
    botController.messages.launchIn(scope)
    awaitCancellation()
}

private fun initializeDb(context: AppContext) {
    Database.connect("jdbc:sqlite:${context.dbPath}", "org.sqlite.JDBC")
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Visits)
        SchemaUtils.create(Students)
    }
}

private fun initializeLogger(context: AppContext) {
    QoolloLogger {
        val logbackKey = "logback"
        val filePolicy = FilePolicy(
            fileName = "BotLoggerFiles",
            filesPath = context.volumePath,
            fileSize = 1024 * 1024 * 6,
            filesCount = 5,
            append = true
        )
        registerLogger(logbackKey, LogbackLogger(logbackKey))
        enableConsoleLogging(logbackKey)
        enableFileLogging(logbackKey, filePolicy)
    }
}
