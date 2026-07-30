# Room database rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class androidx.room.MultiInstanceInvalidationService

# Keeps the UrgencyLevel enum from being renamed (important for database storage)
-keepclassmembers enum com.example.timely.UrgencyLevel {
    *;
}
