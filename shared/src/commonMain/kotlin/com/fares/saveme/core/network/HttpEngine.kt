package com.fares.saveme.core.network

import io.ktor.client.engine.HttpClientEngine

expect fun httpEngine(): HttpClientEngine
