package me.matsumo.fankt

import de.jensklingenberg.ktorfit.Ktorfit
import me.matsumo.fankt.datasource.FanboxApi
import me.matsumo.fankt.datasource.createFanboxCreatorApi
import me.matsumo.fankt.datasource.createFanboxPostApi
import me.matsumo.fankt.datasource.createFanboxSearchApi
import me.matsumo.fankt.datasource.createFanboxUserApi
import me.matsumo.fankt.datasource.impl.FanboxApiImpl

class Fankt {

    fun buildFanboxApi(): FanboxApi {
        val ktorfit = Ktorfit.Builder()
            .baseUrl(FANBOX_API_BASE_URL)
            .build()

        return FanboxApiImpl(
            postApi = ktorfit.createFanboxPostApi(),
            creatorApi = ktorfit.createFanboxCreatorApi(),
            userApi = ktorfit.createFanboxUserApi(),
            searchApi = ktorfit.createFanboxSearchApi(),
        )
    }

    companion object {
        private const val FANBOX_API_BASE_URL = "https://api.fanbox.cc"
    }
}
