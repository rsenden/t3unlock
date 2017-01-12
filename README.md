# T3Unlock

# This utility is provided as-is, use it at your own risk. I cannot be held responsible for any damage to any of your devices due to the use of this utility.#

This is a simple Java utility for unlocking (logging in to) a Samsung portable SSD T3 drive on any platform that has a libusb implementation. Since Samsung doesn't provide a utility for logging in to the drive under Linux, this utility was specifically developed for use under Linux.

In order to use this utility, you will need to have Java and libusb installed. To build from source, simple check out the code and run 'mvn clean package'. A binary distribution will be provided later.

To unlock your SSD, simply run 'java -jar T3Unlock-1.0.jar [password]'. Note that it is normal behavior to see the following error message after processing has completed: 'USB error 5: Unable to re-attach kernel driver: Entity not found'

# TODO
* Rewrite this to a native Linux utility (C/python/perl/...) to avoid Java dependency
* Add support for other T3 operations (set password, change password, ...)

