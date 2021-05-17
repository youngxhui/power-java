import core.Power


fun main() {
    val power = Power(8080, "server")
    power.start()
    power.blockUntilShutdown()
}