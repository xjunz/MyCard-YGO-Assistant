package xjunz.tool.mycard.util

import io.ktor.http.*

open class HttpStatusCodeException(val code: HttpStatusCode) : RuntimeException()