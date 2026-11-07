package io.github.terminalis.machlauncher

open class ChangeNotifier {
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    protected fun notifyListeners() {
        for (listener in listeners.toList()) listener()
    }
}
