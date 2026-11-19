package io.threadtamer.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import javax.inject.Inject

abstract class ThreadTamerExtension @Inject constructor(factory: ObjectFactory) {
    val active: Property<Boolean> = factory.property(Boolean::class.java).convention(true)
    val ignoredTypePrefixes: ListProperty<String> = factory.listProperty(String::class.java).convention(
        listOf(
            "io.threadtamer.runtime.",
            "kotlin.",
            "kotlinx.",
            "java.",
            "javax.",
            "android.",
            "androidx."
        )
    )
    /** Optional build-specific override. The plugin uses its packaged route table when unset. */
    val ruleBook: RegularFileProperty = factory.fileProperty()
}
