package com.fares.saveme.feature.server

import com.fares.saveme.core.config.ServerAddressSettings
import com.fares.saveme.core.network.httpEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val serverModule = module {
    viewModel {
        ServerAddressViewModel(
            settings = get<ServerAddressSettings>(),
            // A bare client with its own engine (closed after the probe): the health route is public and the address under test is not the configured one.
            probe = { hostPort ->
                HttpClient(httpEngine()) {
                    expectSuccess = false
                    install(HttpTimeout) { requestTimeoutMillis = 5_000; connectTimeoutMillis = 5_000 }
                }.use { it.get("http://$hostPort/up").status.isSuccess() }
            },
        )
    }
}
