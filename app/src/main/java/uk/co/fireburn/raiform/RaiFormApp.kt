package uk.co.fireburn.raiform

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RaiFormApp : Application() {
    // This class is required to generate the Hilt component.
    // You can also initialize global libraries here (e.g., Timber for logging) if needed later.
}
