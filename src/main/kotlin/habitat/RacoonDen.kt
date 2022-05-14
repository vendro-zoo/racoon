package habitat

import commons.query.Ping
import habitat.configuration.RacoonConfiguration
import java.sql.SQLException

object RacoonDen {
    private val availableManagers: ArrayDeque<RacoonManager> = ArrayDeque()
    private val unavailableManagers: MutableSet<RacoonManager> = mutableSetOf()

    fun getManager(): RacoonManager {
        val settings = RacoonConfiguration.Connection.getDefault()

        if (settings.maxManagers != 0 &&
            availableManagers.isEmpty() &&
            unavailableManagers.size >= settings.maxManagers)
            throw SQLException("The maximum number of managers has been reached")


        // If there are available managers, return the first still available one, if exists
        while (availableManagers.isNotEmpty()) {
            // Get the first available manager
            val manager = availableManagers.removeLast()

            // Check if the manager is still available, if not, check the next one
            if (!this.ping(manager)) continue

            // If the manager is available, add it to the unavailable list
            unavailableManagers.add(manager)

            return manager
        }

        // Return a new manager
        val manager = RacoonManager.fromSettings(settings)
        unavailableManagers.add(manager)
        return manager
    }

    /**
     * Re-inserts a manager into the pool.
     *
     * @param manager The manager to re-insert.
     * @return True if the manager was re-inserted, false otherwise.
     */
    fun releaseManager(manager: RacoonManager): Boolean {
        // Moves the manager to the available list
        if (availableManagers.size >= RacoonConfiguration.Connection.getDefault().maxPoolSize) return false
        unavailableManagers.remove(manager)
        availableManagers.addLast(manager)
        return true
    }

    /**
     * Checks if the manager is available by running a ping query.
     *
     * @param manager The manager to check
     * @return True if the manager is available, false otherwise
     */
    private fun ping(manager: RacoonManager): Boolean {
        try {
            // Executing a ping query to the database to check if the connection is still alive
            val ping = manager.createQueryRacoon("SELECT 1 ping").mapToClass<Ping>()

            // If the result is not 1, the connection is not alive
            if (ping.isEmpty() || ping[0].ping != 1.toByte()) return false
        } catch (e: SQLException) {
            // If an exception is thrown, the connection is not alive
            return false
        }

        // Otherwise, the connection is alive
        return true
    }
}