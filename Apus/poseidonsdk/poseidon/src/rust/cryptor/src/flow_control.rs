use std::{
    collections::HashMap,
    sync::{Arc, LazyLock, RwLock},
};

use crc32fast::Hasher;

/// Represents a flow control mechanism associated with a specific device.
///
/// The `FlowControl` struct manages a store of u32 pairs and is uniquely identified
/// by a `device_id`. It provides functionalities to determine if the flow is passable
/// based on a given scale and manages cached values using a hash map.
pub(crate) struct FlowControl {
    store: RwLock<HashMap<u32, u32>>,
    device_id: String,
}

impl FlowControl {
    /// Returns an `Arc<FlowControl>` instance associated with the given `device_id`.
    ///
    /// If an instance with the specified `device_id` already exists, it returns
    /// a clone of the existing instance. Otherwise, it creates a new `FlowControl`
    /// instance, stores it, and returns a clone of the newly created instance.
    ///
    /// # Arguments
    ///
    /// * `device_id` - A `String` that uniquely identifies the device for which
    ///   the `FlowControl` instance is managed.
    ///
    /// # Return
    ///
    /// An `Arc<FlowControl>` corresponding to the `device_id`.

    pub(crate) fn instance(device_id: String) -> Arc<FlowControl> {
        static INSTANCES: LazyLock<RwLock<HashMap<String, Arc<FlowControl>>>> =
            LazyLock::new(|| RwLock::new(HashMap::new()));
        {
            let map = INSTANCES.read().unwrap();
            if let Some(flow) = map.get(&device_id) {
                return Arc::clone(flow);
            }
        }

        let mut map = INSTANCES.write().unwrap();
        map.entry(device_id.clone())
            .or_insert_with(|| Arc::new(FlowControl::new(device_id.clone())))
            .clone()
    }

    fn new(device_id: String) -> Self {
        Self {
            store: RwLock::new(HashMap::new()),
            device_id,
        }
    }

    /// Given a scale between 0 and 100, determine if the flow control is passable.
    ///
    /// # Return
    ///
    /// `true` if the flow control is passable, `false` otherwise.
    ///
    /// # Notes
    ///
    /// If `scale` is 0, always returns `false`.
    ///
    /// If `scale` is 100, always returns `true`.
    ///
    /// Otherwise, the decision is based on a checksum of the `device_id` property.
    /// The checksum is taken modulo `scale` and compared with `scale`. If the
    /// result is greater than 0, this function returns `true`; otherwise, it
    /// returns `false`.
    pub(crate) fn is_passable(&self, mut scale: u32) -> bool {
        if scale > 100 {
            scale = 100;
        }

        if scale == 0 {
            return false;
        }

        if scale == 100 {
            return true;
        }

        let key = 100;
        let v: u32 = self.get_or_insert(key, || {
            let mut hasher = Hasher::new();
            hasher.update(self.device_id.as_bytes());
            let checksum = hasher.finalize();
            checksum % key + 1
        });
        return if v > 0 { v <= scale } else { false };
    }

    fn get_or_insert<F>(&self, key: u32, f: F) -> u32
    where
        F: FnOnce() -> u32,
    {
        {
            let map = self.store.read().unwrap();
            if let Some(&value) = map.get(&key) {
                return value;
            }
        }

        let mut map = self.store.write().unwrap();
        if let Some(&value) = map.get(&key) {
            return value;
        }

        let new_value = f();
        map.insert(key, new_value);
        new_value
    }
}
