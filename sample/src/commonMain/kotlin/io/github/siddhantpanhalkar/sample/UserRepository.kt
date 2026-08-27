package io.github.siddhantpanhalkar.sample

// Exported to ObjC and referenced from `iosApp/ContentView.swift`.
class UserRepository {
    fun getUser(id: String): String = "User:$id"
    fun saveUser(id: String, name: String) {}
}
