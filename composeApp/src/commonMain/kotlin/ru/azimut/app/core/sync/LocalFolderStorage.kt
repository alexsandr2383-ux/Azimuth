package ru.azimut.app.core.sync

/**
 * A plain local/USB folder as the sync remote. Desktop: an absolute directory path;
 * Android: a SAF document tree URI string obtained from the folder picker.
 */
expect class LocalFolderStorage(config: String) : RemoteStorage
