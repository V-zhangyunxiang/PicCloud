# 什么是 Android Jetpack

Android Jetpack 是 Google 在 2018 年 I/O 大会上推出的一套库、工具和指南的集合，基于AndroidX(原 Support 库的升级版)构建，覆盖从「架构设计」到「UI开发」、「后台任务」、「依赖管理」等全开发流程，它不是一个独立的框架，而是将 Android 开发中常用的核心能力拆分为**可按需引入的模块化组件**，开发者可根据需求选择使用，无需整体依赖。

# 它解决了什么问题

Jetpack 的设计完全围绕「解决 Android 开发痛点」展开，核心目标可概括为四点。

## 目标一：简化生命周期管理，根除内存泄漏

传统开发中，Activity/Fragment 生命周期与业务逻辑(如异步任务、网络请求)强耦合，易导致「任务未完成时页面销毁」引发的内存泄漏，Jetpack 通过 Lifecycle、ViewModel 等组件，**让业务逻辑与页面生命周期解耦**，自动感知页面状态并调整执行逻辑。

## 目标二：促进架构标准化，降低协作成本

传统开发中，团队对「数据如何存储」「UI 如何更新」缺乏统一标准，易出现代码混乱，Jetpack 提供「ViewModel + LiveData + Room + Navigation」的标准化 MVVM 架构方案，定义清晰的职责边界(数据层、UI 层、导航层)，让团队开发风格统一。

## 目标三：解决跨版本兼容问题，减少适配成本

Android 系统版本碎片化严重，传统开发需编写大量 Build.VERSION.SDK_INT >= XX 的判断逻辑，Jetpack 基于 AndroidX 封装底层 API，开发者调用统一接口即可，版本适配由 Jetpack 内部处理。

## 目标四：减少模板代码，提升开发效率

传统开发中，findViewById、SQLiteOpenHelper、onSaveInstanceState 等重复模板代码占用大量时间，Jetpack 通过 View Binding、Room、ViewModel 等组件彻底消除这类代码，让开发者聚焦业务逻辑而非基础工具编写。

# Jetpack 核心组件如何分类，每种分类有哪些常用组件

Jetpack 的组件按「功能场景」可分为 4 大类，每类组件聚焦特定开发需求，且支持独立引入。

![[Jetpack 组成.png]]

## 架构组件

标准化 App 架构，解决「数据与 UI 协同」问题。

定义「数据存储、UI更新、页面导航」的标准流程，**避免业务逻辑与 UI 强耦合**，常用组件包括 Lifecycle、ViewModel、LiveData、Room、Navigation。

# Lifecycle 组件

Lifecycle 是 Jetpack 提供的**生命周期感知**组件，它通过观察者模式封装了 Activity/Fragment 等组件的生命周期状态，允许其他组件 (如网络请求管理器、播放器、定位工具) 自动感知并响应这些状态变化，无需手动编写生命周期回调逻辑。

**LifecycleOwner (生命周期持有者)**

角色：拥有生命周期的“被观察者”，即能提供自身生命周期状态的组件。  

作用：通过 getLifecycle() 方法暴露自身的生命周期对象，让观察者可以监听。  

默认实现：AndroidX 中的 ComponentActivity(Activity 的父类)和 Fragment 已默认实现LifecycleOwner 接口，因此所有 Activity 和 Fragment 天生就是 LifecycleOwner。

**LifecycleObserver (生命周期观察者)**

角色：需要感知生命周期变化的“观察者”，通常是业务组件(如定位工具、播放器)。  

作用：定义生命周期状态变化时的回调逻辑(如“在 Resume 时启动”，“在 Paused 时停止”)。  

实现方式：通过接口 (DefaultLifecycleObserver) 定义回调方法。

**LifecycleRegistry (生命周期管理器)**

角色：连接 LifecycleOwner 和 LifecycleObserver 的“协调者”，负责管理生命周期状态并通知观察者。  

作用：维护 LifecycleOwner 的当前生命周期状态，当状态变化时，自动调用所有注册的LifecycleObserver 的对应回调方法，并处理观察者的注册/解注册逻辑。  

**三者关系:**

LifecycleOwner 持有 LifecycleRegistry → LifecycleObserver 向 LifecycleRegistry 注册 → LifecycleRegistry 在 LifecycleOwner 状态变化时，通知所有 LifecycleObserver。

## 如何通过 LifecycleObserver 感知 Activity/F 的生命周期变化

示例：实现一个“自动暂停/恢复的定位工具”。

步骤一：定义 LifecycleObserver 观察者

```kotlin
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

// 定位工具作为观察者，实现 DefaultLifecycleObserver
class LocationObserver : DefaultLifecycleObserver {  
      
     // 当LifecycleOwner进入RESUMED状态时（对应Activity的onResume）      
       override fun  onResume(owner: LifecycleOwner) {  
        super.onResume(owner)         
        startLocation() // 启动定位     
    }      
    
     // 当LifecycleOwner进入PAUSED状态时（对应Activity的onPause）      
       override fun  onPause(owner: LifecycleOwner) {   
           super.onPause(owner)         
           stopLocation()  // 停止定位   
     }      
    
    // 当LifecycleOwner销毁时（对应Activity的onDestroy）
    override fun  onDestroy(owner: LifecycleOwner) {   
        super.onDestroy(owner)       
        release() // 释放资源，避免内存泄漏 
     }      
     
    private fun  startLocation() {        
       println("开始定位...")     
    }      
    
    private fun  stopLocation() {  
      println("停止定位...")     
    }      
    
    private fun  release() {      
       println("释放定位资源...")     
    }  
}
```

步骤二：在 Activity(LifecycleOwner) 中注册观察者

```kotlin
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun  onCreate(savedInstanceState: Bundle?) {
              
        super.onCreate(savedInstanceState)      
        setContentView(R.layout.activity_main)    
         // 创建观察者实例      
        val  locationObserver = LocationObserver()
          // 向Activity的Lifecycle注册观察者     
          lifecycle.addObserver(locationObserver)  
    }
}
```
## 自定义 LifecycleOwner 需要注意哪些问题

默认情况下，只有 Activity/Fragment 是 LifecycleOwner，但有时需要为自定义组件(如PlayerManager、DownloadManager) 实现生命周期管理，此时需自定义 LifecycleOwner。

自定义 LifecycleOwner 的实现步骤，核心思路如下。  

1、实现 LifecycleOwner 接口，持有 LifecycleRegistry 实例。 

2、手动管理生命周期状态(通过 LifecycleRegistry 的 handleLifecycleEvent 方法分发事件）。  

3、允许其他观察者注册监听。

示例：自定义一个“播放器管理器”作为 LifecycleOwner。

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
// 自定义播放器管理器，实现 LifecycleOwner  
class PlayerManager : LifecycleOwner {  
     // 1. 持有LifecycleRegistry实例        
      private val  lifecycleRegistry = LifecycleRegistry(this)      
     // 2. 实现 LifecycleOwner 的 getLifecycle() 方法     
         override fun  getLifecycle(): Lifecycle = lifecycleRegistry      
      // 模拟播放器的“启动”（对应生命周期 STARTED）        
        fun  start() {    
          // 分发 STARTED 事件（会触发观察者的 onStart 回调）
          lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)                 
          println("播放器启动")        
    }      
        // 模拟播放器的“暂停”（对应生命周期 PAUSED）         
           fun  pause() {  
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)       
            println("播放器暂停")              
    }      
              // 模拟播放器的“销毁”（对应生命周期 DESTROYED）            
           fun  destroy() {  
             lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)         
            println("播放器销毁")                   
    }                  
}
```

使用自定义 LifecycleOwner：

```kotlin
// 定义一个观察者，监听 PlayerManager 的生命周期  
class PlayerObserver : DefaultLifecycleObserver {  
          
    override fun  onStart(owner: LifecycleOwner) {  
        super.onStart(owner)         
        println("观察者：播放器已启动，开始缓冲视频")   
           
    }      
    
    override fun  onPause(owner: LifecycleOwner) {     
         super.onPause(owner)         
        println("观察者：播放器已暂停，保存播放进度")   
    }  
}  
  
// 测试代码  
fun  main() {        
  val  playerManager = PlayerManager()      
  // 注册观察者      
   playerManager.lifecycle.addObserver(PlayerObserver())      
// 模拟播放器状态变化    
   playerManager.start()  // 触发 ON_START → 观察者 onStart 被调用      
   playerManager.pause()  // 触发 ON_PAUSE → 观察者 onPause 被调用       
   playerManager.destroy() // 触发 ON_DESTROY  
}
```

# ViewModel 组件

ViewModel 是 Jetpack 架构组件之一，用于存储和管理与 UI 相关的数据，其生命周期与Activity/Fragment 的“配置变更”(如屏幕旋转、语言切换、分屏模式)无关，即使页面因配置变更重建，ViewModel 实例也会被保留，新页面可直接复用旧 ViewModel 中的数据。

ViewModel 的生命周期与它所关联的 LifecycleOwner(如Activity/Fragment)的“完整生命周期”绑定，
在 LifecycleOwner 的整个生命周期中，ViewModel 始终存活，当 LifecycleOwner 真正销毁时(如Activity 调用 finish()、Fragment 调用 remove() )，系统调用 ViewModel 的 onCleared() 方法，ViewModel 才会被销毁。

ViewModelProvider 是管理 ViewModel 生命周期的工具类，通过它获取 ViewModel 可确保配置变更时实例复用。

- 使用 Kotlin 委托
  private val myViewModel: MyViewModel by viewModels()
- 手动创建 ViewModelProvider
  ViewModelProvider(this).get(MyViewModel-class.java)
## 如何实现多个 Fragment 共享同一个 ViewModel 实例

让它们从同一个“父 LifecycleOwner”(通常是所属的 Activity)获取 ViewModel，因为同一个 Activity 的 ViewModelProvider 会返回相同的 ViewModel 实例。

1、使用 activityViewModels() 委托，它会自动从当前Fragment 所属的 Activity 中获取 ViewModel。

2、所有共享数据的 Fragment 必须依附于同一个 Activity，否则会获取到不同实例。

3、数据变更通过 LiveData 通知，确保 UI 实时同步。

## ViewModel 中可以直接持有 Context 引用吗

不建议直接持有 Activity/Fragment 的 Context，原因是 ViewModel 的生命周期长于 Activity，会导致已销毁的 Activity 无法被 GC 回收，造成内存泄漏。

使用 Application Context，Application 的生命周期与 App 一致，不会因配置变更而销毁，因此在ViewModel 中持有 Application Context 是安全的，实现方式：继承 AndroidViewModel(ViewModel的子类，专门用于接收 Application）。
# LiveData 组件

LiveData 是 Jetpack 提供的可观察的数据持有者，它可以包装任意数据类型(如 String、List<>），并在数据变化时通知观察者，其核心优势是结合了“可观察性”和“生命周期感知能力”，是 ViewModel 与 UI 层之间传递数据的最佳载体。

LiveData 与传统观察者模式(如 EventBus、RxJava 未处理生命周期时)的核心差异在于内置生命周期感知能力，解决了传统观察者模式中“手动管理注册/注销”的痛点，是更安全的跨组件通信方案。

## 为什么推荐对外暴露 LiveData 而非 MutableLiveData

核心目的是保证数据修改的封装性和安全性，遵循“单一数据源”原则。ViewModel 是数据的“唯一管理者”，若对外暴露 MutableLiveData，UI 层(如 Activity/Fragment)可能直接修改数据，导致数据流向混乱(不知道数据是从 ViewModel 还是 UI 层修改的)。仅在 ViewModel 内部保留 MutableLiveData 的修改权限，所有数据变更都通过 ViewModel 的方法进行，便于调试和维护。

## 如何通过 LiveData 的转换方法(map、switchMap)处理数据转换逻辑

LiveData 提供 Transformations.map() 和 Transformations.switchMap() 两个工具方法，用于在LiveData 数据传递到观察者前进行转换，避免在 UI 层处理转换逻辑，符合“数据处理在 ViewModel中完成”的原则。

**map：将一种类型的 LiveData 转换为另一种类型**  

适用场景：对源 LiveData 的数据进行加工(如从对象中提取字段、格式转换)。

**switchMap：根据源 LiveData 的值切换到另一个 LiveData**  

适用场景：当源 LiveData 的值变化时，需要动态切换到一个新的 LiveData(如根据用户 ID 加载不同用户的数据)。

**核心区别：**

map 是“一对一转换”，输入和输出都是单个 LiveData。  

switchMap 是“一对多切换”，输入是一个 LiveData，输出是随输入值变化的多个 LiveData。

## 什么是“LiveData 粘性事件”？如何避免或解决这一问题？

LiveData 粘性事件，指当观察者注册到 LiveData 时，若 LiveData 已有数据(或之前发送过数据)，观察者会立即收到该历史数据，类似“粘性”地粘住了旧事件。

LiveData 的设计初衷是“确保 UI 始终显示最新数据”，因此会保存最新的数据快照，当新观察者注册时，无论数据是何时更新的，LiveData 都会将最新数据推送给观察者，这在多数场景(如显示列表、详情)是合理的，但在“事件通知”场景(如弹窗、导航)可能导致问题(如重复处理事件)。

**方案一：使用 SingleLiveEvent(处理一次性事件)**

SingleLiveEvent 是 Google 提供的 LiveData 子类，仅将事件分发给最近注册的活跃观察者，且只触发一次(适用于弹窗、导航等一次性事件)。

**方案二：封装事件类，标记是否已处理**  

通过自定义事件类，记录事件是否被消费，避免重复处理。

# Room 组件

Jetpack 提供的 SQLite 对象映射库，它在 SQLite 基础上封装了一层抽象，允许开发者通过注解而非手写 SQL 语句操作数据库，同时保留 SQLite 的性能优势，是 Android 官方推荐的本地结构化数据存储方案。

## 与原生 SQLite 的区别是什么

| 特性维度             | 原生 SQLite (SQLiteOpenHelper)                                 | Room (Jetpack 组件)                      |
| ---------------- | ------------------------------------------------------------ | -------------------------------------- |
| **抽象级别**         | 底层 API，需直接与数据库引擎交互                                           | 高层 ORM 框架，封装了底层细节                      |
| **代码量与样板代码**     | 代码量巨大，需手动编写 `SQLiteOpenHelper`、`ContentValues`、解析 `Cursor` 等 | 代码量极少，通过注解定义，大幅减少样板代码                  |
| **SQL 检查**       | **运行时**检查，写错表名或列名要等到 App 运行崩溃才能发现                            | **编译时**验证，SQL 语法错误在编译期即可发现，避免运行时崩溃     |
| **数据转换**         | 需要手动将 `Cursor` 中的数据转换为对象，过程繁琐且易错                             | 自动完成对象与数据表行之间的映射，无需手动转换                |
| **与 Jetpack 集成** | 无原生支持                                                        | 与 `LiveData`、`Flow` 无缝集成，数据变化可自动通知 UI  |
| **协程支持**         | 需自行处理异步和线程                                                   | DAO 方法可声明为 `suspend` 函数，完美支持 Kotlin 协程 |
| **数据库迁移**        | 手动编写复杂的事务脚本，管理困难                                             | 提供便捷的迁移路径，通过 `Migration` 类简化版本升级       |

## 原生 SQLite 与 Room 代码对比

### 使用原生 SQLite (Java)

需要继承 `SQLiteOpenHelper`，并手动处理数据的读写和转换。

```java
// 1. 创建数据库帮助类
public class UserDbHelper extends SQLiteOpenHelper {
    // ... 数据库名、版本、表创建语句等常量

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT, " +
                "age INTEGER)";
        db.execSQL(CREATE_TABLE);
    }
    // ... 其他方法
}

// 2. 执行插入操作
UserDbHelper dbHelper = new UserDbHelper(context);
SQLiteDatabase db = dbHelper.getWritableDatabase();

ContentValues values = new ContentValues();
values.put("name", "张三");
values.put("age", 25);
long newRowId = db.insert("users", null, values); // 返回新行的ID

// 3. 执行查询操作
SQLiteDatabase db = dbHelper.getReadableDatabase();
String[] projection = {"name", "age"};
String selection = "id = ?";
String[] selectionArgs = {"1"};

Cursor cursor = db.query(
    "users",   // 表名
    projection, // 要返回的列
    selection,  // WHERE 子句
    selectionArgs, // WHERE 子句的值
    null, null, null
);

// 手动遍历 Cursor 并解析数据
List<User> userList = new ArrayList<>();
while(cursor.moveToNext()) {
    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
    int age = cursor.getInt(cursor.getColumnIndexOrThrow("age"));
    userList.add(new User(name, age));
}
cursor.close();
db.close();
```

### 使用 Room (Kotlin)

Room 的代码更加简洁、清晰。

```kotlin
// 1. 定义Entity
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "name") val name: String,
    val age: Int
)

// 2. 定义DAO
@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Int): User
}

// 3. 定义Database
@Database(entities = [User::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

// 4. 在 ViewModel 或 Repository 中使用
val db = Room.databaseBuilder(
    applicationContext,
    AppDatabase::class.java,
    "app_database"
).build()

viewModelScope.launch {
    // 插入数据
    db.userDao().insertUser(User(1, "张三", 25))
    // 查询数据
    val user = db.userDao().getUserById(1)
    println("User: ${user.name}, Age: ${user.age}")
}
```

## Room 的三大核心组件

**Entity(实体类)：**

**用 `@Entity` 注解的数据类**，代表数据库中的一张表。类的每个属性通常对应表中的一列，默认列名与字段名一致(可通过 @ColumnInfo 自定义)。

核心注解：`@Entity`，标记一个类为数据库实体。

- **`tableName`**：指定表名。默认使用类名。
- **`primaryKeys`**：定义**复合主键**(由多个列共同组成)，每个实体必须至少有一个，**`autoGenerate`**：设为 `true` 时，主键值将由数据库自动生成。
- **`indices`**：为指定列创建**索引**，提升查询速度。也可以创建唯一索引。
- **`foreignKeys`**：定义**外键约束**，维护表间关系。可指定当父表更新或删除时的行为(如 `CASCADE`)。
- **`ignoredColumns`**：强制忽略从父类继承的字段。
- **`@ColumnInfo`**：自定义列名。

```kotlin
@Entity(tableName = "users") // 表名为 users，而非 User
@Entity(primaryKeys = ["firstName", "lastName"])
@Entity(indices = [Index(value = ["lastName"], unique = true)])
@Entity(foreignKeys = [ForeignKey(
    entity = User::class,
    parentColumns = ["id"],
    childColumns = ["userId"],
    onDelete = ForeignKey.CASCADE // 用户删除，其所有订单也删除
)])
data class User(
@PrimaryKey(autoGenerate = true) // autoGenerate=true：自增ID
val id: Long = 0,
// 普通列（自定义列名）
@ColumnInfo(name = "user_name") // 列名改为user_name
val name: String,
)
```

**DAO(数据访问对象)**：

**用 `@Dao` 注解的接口或抽象类**，定义所有数据库操作方法(增删改查)。这是 Room 的核心，所有数据库操作都通过 DAO 进行。

核心注解：`@Dao`，标记一个接口或抽象类为数据访问对象。

**`@Insert`**：插入一个或多个实体，返回值为插入行的 `rowId`(`Long` 或 `List<Long>`)。
**`@Update`**：根据主键更新一个或多个实体，返回值为“受影响的行数”。
**`@Delete`**：根据主键删除一个或多个实体，返回值为“受影响的行数”。
**`@Upsert`**：**插入或更新**。如果数据存在则更新，否则插入。

**`@Query`**：编写自定义 SQL 语句进行查询。最灵活的注解，返回值可以是**实体类、基本类型或LiveData** 等。
```kotlin
@Query("SELECT * FROM users WHERE age > :minAge")
suspend fun getUsersOlderThan(minAge: Int): List<User>
```

**`@Transaction`**：确保方法内的多个数据库操作在**同一个事务**中执行。

**可观察查询**：方法返回 `LiveData` 或 `Flow`，数据变化时自动发出新数据。
```kotlin
@Query("SELECT * FROM users")
fun getAllUsers(): Flow<List<User>>
```

**协程支持**：将 DAO 方法声明为 `suspend`，可在协程中安全地进行数据库操作。

**Database(数据库持有者)：**

**用 `@Database` 注解的抽象类**，继承自 `RoomDatabase`。它是数据库的持有者，也是应用获取 DAO 实例的主要入口。

核心注解：`@Database`，标记一个抽象类为 Room 数据库。

- **`entities`**：指定该数据库包含的所有实体类。
- **`version`**：数据库的**版本号**。当实体类结构变化时，需要增加此版本号。
- **`exportSchema`**：是否导出数据库 Schema 文件，默认 `true`。

**三者关系总结：**  

Database → 包含多个 Entity (表) → 通过抽象方法提供 Dao 实例 → Dao 通过注解操作 Entity(表数据)。

## 如何实现 Room 数据库的版本迁移

当 App 升级需要修改数据库结构(如新增表、字段)时，需通过“版本迁移”保证旧数据不丢失。

**自动迁移**：使用 `@AutoMigration` 注解，Room 会尝试自动处理简单的 Schema 变更。
```kotlin
@Database(
    version = 2,
    entities = [User::class],
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
```

**手动迁移**：通过 `Migration` 类编写 SQL 脚本处理复杂变更，在数据库构建时添加addMigrations()。
```kotlin
// 1. 定义从版本1到版本2的迁移（新增age字段）
val MIGRATION_1_TO_2 = object : Migration(1, 2) {
   override fun migrate(database: SupportSQLiteDatabase) {
  // 执行SQL添加字段（假设 User 表新增 age 列）
  database.execSQL("ALTER TABLE users ADD COLUMN age INTEGER DEFAULT 0")
   }
}
  // 在构建 Database 实例时添加
  Room.databaseBuilder(...).addMigrations(MIGRATION_1_2).build()
```

**迁移策略选择建议：**  

生产环境(简单变更)：用自动迁移。

生产环境(复杂变更)：用手动迁移(精确控制 SQL 逻辑)。

# ViewBinding 组件

适用于仅需安全访问控件的场景(如大多数页面)，希望保留 xml 的纯粹性，避免布局中混入逻辑，同时消除 findViewById 的空指针风险。

![[DB VS VB.png]]

# Navigation 组件

Navigation 是 Jetpack 提供的导航组件，用于统一管理 App 内的页面跳转(包括 Activity、Fragment），通过导航图(nav_graph.xml)集中定义跳转关系，支持可视化编辑，是官方推荐的导航解决方案。

# WorkManager 组件

WorkManager 是 Jetpack 提供的**后台任务管理库**，用于处理**可延期**的后台任务。即使 **App 退出或设备重启也能保证任务执行**，它自动适配 Android 版本(如 API 23+ 用 JobScheduler，API 22- 用 AlarmManager + BroadcastReceiver)，无需开发者手动处理版本差异。

> 不适合即时性任务和前台可见任务。

## WorkManager 的核心类分别是什么

WorkManager 通过三个核心类协同完成任务调度，各自职责明确。

![[WorkManager 核心类.png]]

## 如何创建一次性任务和周期性任务

### 创建一次性任务(OneTimeWorkRequest)：

用于执行一次的任务(如单次日志上传、临时数据同步)，创建步骤：

**步骤一：定义 Worker 子类(任务逻辑)**

```kotlin
class OneTimeSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // 任务逻辑：如同步本地数据到服务器
        syncLocalDataToServer()
        // 返回结果：成功、失败或重试
        return Result.success()
        // 可选：Result.failure()（失败）、Result.retry()（需配合重试策略）
    }
}
```

**步骤二：创建 OneTimeWorkRequest(配置任务)**

```kotlin
// 配置一次性任务
val oneTimeWorkRequest = OneTimeWorkRequestBuilder<OneTimeSyncWorker>()
    .setInitialDelay(5, TimeUnit.MINUTES) // 延迟5分钟执行
    .setBackoffCriteria(
        BackoffPolicy.LINEAR, // 重试策略：线性延迟（失败后10s、20s、30s...重试）
        10, TimeUnit.SECONDS // 初始重试延迟
    )
    .build()
```

**步骤三：通过 WorkManager 调度任务**

> WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)

### 创建周期性任务(PeriodicWorkRequest)：

用于按**固定周期重复执行**的任务(如每小时同步一次数据)，注意最小周期为 15 分钟(系统限制)。

**步骤一：定义 Worker 子类(同一次性任务)**

**步骤二：创建 PeriodicWorkRequest(配置周期)**

```kotlin
val periodicWorkRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
    repeatInterval = 1, TimeUnit.HOURS, // 重复周期：1小时
    flexTimeInterval = 15, TimeUnit.MINUTES // 弹性时间：允许在周期最后 15 分钟内执行（减少电量消耗）
)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // 仅在联网时执行
            .build()
    )
    .build()
```

`flexTimeInterval` 必须小于等于 `repeatInterval`。

**步骤三：调度周期性任务**

> WorkManager.getInstance(context).enqueue(periodicWorkRequest)

## 如何实现 WorkManager 任务链

WorkManager 支持**链式任务**和**并行任务**，同时提供状态监听机制，确保任务执行可追溯。

### 链式任务

通过 beginWith() 和 then() 定义任务执行顺序(如 A 完成后执行 B，B 完成后执行 C）。

```kotlin
// 定义三个任务
val workA = OneTimeWorkRequestBuilder<WorkA>().build()
val workB = OneTimeWorkRequestBuilder<WorkB>().build()
val workC = OneTimeWorkRequestBuilder<WorkC>().build()

// 构建任务链：A → B → C
WorkManager.getInstance(context)
    .beginWith(workA) // 先执行A
    .then(workB)      // A完成后执行B
    .then(workC)      // B完成后执行C
    .enqueue()
```
### 并行任务

通过 beginWith() 传入多个任务，实现并行执行(如 A 和 B 同时执行，两者都完成后执行 C）。

```kotlin
// 并行执行 A 和 B，两者完成后执行 C
WorkManager.getInstance(context)
.beginWith(listOf(workA, workB)) // A 和 B 并行执行
.then(workC)                                // A 和 B 都完成后执行 C
.enqueue()
```

## 如何监听任务的执行状态与结果

通过 WorkManager.getWorkInfoByIdLiveData() 获取任务状态的 LiveData，实时监听任务生命周期(ENQUEUED→RUNNING→SUCCEEDED/FAILED/CANCELLED)。

1.设置任务结果：Worker 可通过 Data 类返回结果，在 doWork() 中设置：
```kotlin
// 在Worker中返回结果
override fun doWork(): Result {
    val outputData = Data.Builder()
        .putString("key", "任务执行结果")
        .build()
    return Result.success(outputData)
}
```

2.监听任务状态与结果
```kotlin
// 监听workC的状态
WorkManager.getInstance(context)
    .getWorkInfoByIdLiveData(workC.id)
    .observe(lifecycleOwner) { workInfo ->
        when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> println("任务C已入队")
            WorkInfo.State.RUNNING -> println("任务C执行中")
            WorkInfo.State.SUCCEEDED -> {
                println("任务C执行成功")
                // 获取任务返回结果(若有)
                val resultData = workInfo.outputData.getString("key")
            }
            WorkInfo.State.FAILED -> println("任务C执行失败")
            WorkInfo.State.CANCELLED -> println("任务C已取消")
        }
    }
```

## 如何处理任务的约束条件

WorkManager 通过 Constraints 类定义任务执行的“前置条件”(如必须联网、设备充电时才执行)，确保任务在合适的系统状态下运行，减少不必要的资源消耗。

常用约束条件：

![[WorkManager 约束条件.png]]

约束配置示例：创建“仅在设备充电、联网且存储充足时执行”的日志上传任务

### 步骤一：定义约束
```kotlin
val constraints = Constraints.Builder()
    // 网络约束：需要联网（可选值：NONE、CONNECTED、UNMETERED、NOT_ROAMING）
    .setRequiredNetworkType(NetworkType.CONNECTED)
    // 充电约束：必须在充电时执行
    .setRequiresCharging(true)
    // 存储约束：存储不低时才执行
    .setRequiresStorageNotLow(true)
    .build()
```

### 步骤二：将约束应用到 WorkRequest
```kotlin
val uploadLogWork = OneTimeWorkRequestBuilder<UploadLogWorker>()
    .setConstraints(constraints) // 关联约束
    .build()
```

### 步骤三：调度任务
```kotlin
WorkManager.getInstance(context).enqueue(uploadLogWork)
```

### 约束生效逻辑

1. 任务会先进入 `ENQUEUED` 状态，等待所有约束条件满足。
2. 当约束条件全部满足时，任务自动转为 `RUNNING` 状态。
3. 若执行过程中约束条件不再满足（如突然断网），任务会暂停并重新等待约束满足。

## 当应用进程被杀死后，WorkManager 任务还能执行吗

应用进程被杀死后，任务仍能执行，WorkManager 的核心设计目标是“保证任务的可靠执行”，即使应用进程被杀死(如用户手动关闭App、系统内存不足回收进程)，**已调度的任务**仍会在合适的时机执行。

> 底层调度机制：WorkManager 并非直接运行任务，而是根据 Android 系统版本自动选择最合适的底层调度器，确保任务在**应用进程外**被管理。

# Paging 组件

# Hilt 组件

见 [[Android 组件化知识点#Hilt 使用手册]]

# CameraX 组件

CameraX 是 Jetpack 提供的相机开发库，在原生 Camera API(Camera1、Camera2)基础上封装，提供统一的 API 接口，简化相机预览、拍照、视频录制等功能的开发。通过“用例（UseCase）”抽象相机功能，开发者无需关心设备差异和底层 API，只需配置用例即可快速实现功能。

![[Pasted image 20260901183937.png]]

# Security 组件

Security 是 Jetpack 提供的安全工具库，简化 Android 应用的**数据加密和安全存储**，提供标准化的加密 API，避免开发者因加密逻辑不当导致的安全漏洞。

**添加依赖**

```kotlin
dependencies {
    implementation "androidx.security:security-crypto:1.1.0"
}
```

## 加密 `SharedPreferences`：`EncryptedSharedPreferences`

这是 `SharedPreferences` 的直接替代品，能自动加密**所有的 Key 和 Value**。

```kotlin
// 1. 创建或获取主密钥 (Master Key)
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM) // 指定加密方案[reference:8]
    .build()

// 2. 创建 EncryptedSharedPreferences 实例[reference:9]
val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "encrypted_prefs", // 文件名
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Key 加密方案[reference:10][reference:11]
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Value 加密方案[reference:12]
)

// 3. 像使用普通 SharedPreferences 一样使用
sharedPreferences.edit()
    .putString("user_token", "your_sensitive_token_here")
    .apply()

val token = sharedPreferences.getString("user_token", null)
```

## 加密文件：`EncryptedFile`

用于对文件内容进行加密读写。

```kotlin
// 1. 同样需要先创建 MasterKey
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

// 2. 创建 EncryptedFile 实例
val file = File(context.filesDir, "my_secret_data")
val encryptedFile = EncryptedFile.Builder(
    context,
    file,
    masterKey,
    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB // 文件加密方案[reference:16]
).build()

// 写入加密数据
encryptedFile.openFileOutput().bufferedWriter().use { writer ->
    writer.write("This is a secret message.")
}

// 读取解密数据
encryptedFile.openFileInput().bufferedReader().use { reader ->
    val message = reader.readText()
}
```

## 两大核心组件

- **`MasterKey` (主密钥)**: 这是所有加密操作的起点。它不直接存储你的数据，而是作为一个**密钥加密密钥 (KEK)**，用来加密实际加密数据内容的其他密钥。这个 `MasterKey` 本身会被安全地存储在 **`Android Keystore` 系统**中，确保了极高的安全性。你可以通过 `MasterKey.Builder` 来构建它。
    
- **`Android Keystore`**: 这是一个由操作系统提供的**安全容器**，专门用于存储加密密钥。密钥一旦存入，应用本身也无法直接读取其原始内容，所有加密操作都由系统的安全硬件(如可信执行环境 TEE)完成，极大地提升了密钥被窃取的难度。



