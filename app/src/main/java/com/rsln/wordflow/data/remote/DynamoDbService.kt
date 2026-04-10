package com.rsln.wordflow.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rsln.wordflow.BuildConfig
import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.local.entity.CollectionEntity
import com.rsln.wordflow.data.local.entity.WordCollectionCrossRef
import com.rsln.wordflow.data.local.entity.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DynamoDbService(
    private val settingsDataStore: SettingsDataStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val tableName = "wordflow"

    data class CloudData(
        val words: List<WordEntity>,
        val collections: List<CollectionEntity>,
        val crossRefs: List<WordCollectionCrossRef>,
        val apiKey: String = ""
    )

    suspend fun pushData(
        words: List<WordEntity>,
        collections: List<CollectionEntity>,
        crossRefs: List<WordCollectionCrossRef>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nickname = settingsDataStore.nickname.first()
            if (nickname.isBlank()) return@withContext Result.failure(Exception("No nickname set"))

            val apiKey = settingsDataStore.apiKey.first()
            val dataJson = gson.toJson(mapOf(
                "words" to words,
                "collections" to collections,
                "crossRefs" to crossRefs,
                "apiKey" to apiKey,
                "updatedAt" to System.currentTimeMillis()
            ))

            val item = mapOf(
                "pk" to mapOf("S" to nickname),
                "sk" to mapOf("S" to "DATA"),
                "payload" to mapOf("S" to dataJson)
            )

            val body = gson.toJson(mapOf(
                "TableName" to tableName,
                "Item" to item
            ))

            dynamoRequest("DynamoDB_20120810.PutItem", body)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullData(): Result<CloudData> = withContext(Dispatchers.IO) {
        try {
            val nickname = settingsDataStore.nickname.first()
            if (nickname.isBlank()) return@withContext Result.failure(Exception("No nickname set"))

            val body = gson.toJson(mapOf(
                "TableName" to tableName,
                "Key" to mapOf(
                    "pk" to mapOf("S" to nickname),
                    "sk" to mapOf("S" to "DATA")
                )
            ))

            val responseBody = dynamoRequest("DynamoDB_20120810.GetItem", body)
            val json = JsonParser.parseString(responseBody).asJsonObject

            val item = json.getAsJsonObject("Item")
                ?: return@withContext Result.success(CloudData(emptyList(), emptyList(), emptyList()))

            val payload = item.getAsJsonObject("payload")?.get("S")?.asString
                ?: return@withContext Result.success(CloudData(emptyList(), emptyList(), emptyList()))

            val data = JsonParser.parseString(payload).asJsonObject

            val words = gson.fromJson(
                data.getAsJsonArray("words"),
                Array<WordEntity>::class.java
            )?.toList() ?: emptyList()

            val collections = gson.fromJson(
                data.getAsJsonArray("collections"),
                Array<CollectionEntity>::class.java
            )?.toList() ?: emptyList()

            val crossRefs = gson.fromJson(
                data.getAsJsonArray("crossRefs"),
                Array<WordCollectionCrossRef>::class.java
            )?.toList() ?: emptyList()

            val apiKey = try {
                data.get("apiKey")?.asString ?: ""
            } catch (_: Exception) { "" }

            Result.success(CloudData(words, collections, crossRefs, apiKey))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf("TableName" to tableName))
            dynamoRequest("DynamoDB_20120810.DescribeTable", body)
            Result.success("Connected to DynamoDB table: $tableName")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun dynamoRequest(target: String, body: String): String {
        val accessKey = getAccessKey()
        val secretKey = getSecretKey()
        val region = getRegion()

        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw Exception("AWS credentials not configured")
        }

        val host = "dynamodb.$region.amazonaws.com"
        val endpoint = "https://$host"
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(now)
        val amzDate = dateFormat.format(now)

        val contentHash = sha256Hex(body)

        val canonicalHeaders = "content-type:application/x-amz-json-1.0\n" +
                "host:$host\n" +
                "x-amz-date:$amzDate\n" +
                "x-amz-target:$target\n"
        val signedHeaders = "content-type;host;x-amz-date;x-amz-target"

        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n$contentHash"

        val credentialScope = "$dateStamp/$region/dynamodb/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val signingKey = getSignatureKey(secretKey, dateStamp, region, "dynamodb")
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authorization = "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/x-amz-json-1.0")
            .addHeader("Host", host)
            .addHeader("X-Amz-Date", amzDate)
            .addHeader("X-Amz-Target", target)
            .addHeader("Authorization", authorization)
            .post(body.toRequestBody("application/x-amz-json-1.0".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw Exception("DynamoDB error (${response.code}): $responseBody")
        }

        return responseBody
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).toHex()
    }

    private fun getSignatureKey(key: String, dateStamp: String, region: String, service: String): ByteArray {
        val kDate = hmacSha256("AWS4$key".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "aws4_request")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private suspend fun getAccessKey(): String = BuildConfig.AWS_ACCESS_KEY

    private suspend fun getSecretKey(): String = BuildConfig.AWS_SECRET_KEY

    private suspend fun getRegion(): String = BuildConfig.AWS_REGION
}
