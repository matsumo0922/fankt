package me.matsumo.fankt.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.datasource.FanboxUserApi
import me.matsumo.fankt.datasource.mapper.FanboxUserMapper

internal class FanboxUserRepository(
    private val fanboxUserApi: FanboxUserApi,
    private val fanboxUserMapper: FanboxUserMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getSupportedPlans() = withContext(ioDispatcher) {
        fanboxUserApi.getSupportedPlans().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getPaidRecords() = withContext(ioDispatcher) {
        fanboxUserApi.getPaidRecords().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getUnpaidRecords() = withContext(ioDispatcher) {
        fanboxUserApi.getUnpaidRecords().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getNewsLetters() = withContext(ioDispatcher) {
        fanboxUserApi.getNewsLetters().let {
            fanboxUserMapper.map(it)
        }
    }

    suspend fun getBells(page: Int) = withContext(ioDispatcher) {
        fanboxUserApi.getBells(page).let {
            fanboxUserMapper.map(it)
        }
    }
}