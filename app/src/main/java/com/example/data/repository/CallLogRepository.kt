package com.example.data.repository

import android.content.Context
import android.provider.CallLog
import com.example.data.local.CallLogDao
import com.example.data.local.CallLogEntity
import com.example.data.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CallLogRepository(
    private val context: Context,
    private val dao: CallLogDao
) {

    val allCallLogs: Flow<List<CallLogEntity>> = dao.getAllCallLogs()

    suspend fun insertCallLog(callLog: CallLogEntity) = dao.insertCallLog(callLog)

    suspend fun insertAll(logs: List<CallLogEntity>) = dao.insertAllCallLogs(logs)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun syncSystemCallLogs() = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 50"
            )

            cursor?.use { c ->
                val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)

                val entities = mutableListOf<CallLogEntity>()

                while (c.moveToNext()) {
                    val number = if (numberIdx != -1) c.getString(numberIdx) ?: "" else ""
                    val name = if (nameIdx != -1) c.getString(nameIdx) else null
                    val typeInt = if (typeIdx != -1) c.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durationIdx != -1) c.getLong(durationIdx) else 0L

                    val callTypeStr = when (typeInt) {
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING.name
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED.name
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED.name
                        else -> CallType.INCOMING.name
                    }

                    entities.add(
                        CallLogEntity(
                            phoneNumber = number,
                            contactName = name,
                            callType = callTypeStr,
                            timestamp = date,
                            durationSeconds = duration,
                            isRead = true
                        )
                    )
                }

                if (entities.isNotEmpty()) {
                    dao.insertAllCallLogs(entities)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
