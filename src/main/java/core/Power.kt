package core

import annotation.GRPCServer
import io.grpc.BindableService
import io.grpc.Server
import io.grpc.ServerBuilder
import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Power 启动
 * [port] 启动端口号
 * [scannerPackage] gRPC 实现类包名
 */
class Power(private val port: Int, private val scannerPackage: String) {
    /**
     * gRPC 服务
     */
    private val server = Server()

    private fun Server(): Server {
        val server = ServerBuilder.forPort(port)

        val classes = getClasses(scannerPackage)
        classes.filter { it.getAnnotation(GRPCServer::class.java) != null }
            .filter { it.getDeclaredConstructor().newInstance() is BindableService }.forEach {
                val instance: BindableService = it.getDeclaredConstructor().newInstance() as BindableService
                server.addService(instance)
            }
        val build = server.build()
        return build
    }

    private fun stop() {
        server.shutdown()
    }

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this.stop()
                println("*** server shut down")

            }
        )
    }

    fun blockUntilShutdown() =
        server.awaitTermination()


    private fun getClasses(packagePath: String): List<Class<*>> {
        var packageName = packagePath
        val classes: MutableList<Class<*>> = ArrayList()
        val recursive = true
        // 获取包的名字 并进行替换
        val packageDirName = packageName.replace('.', '/')
        // 定义一个枚举的集合 并进行循环来处理这个目录下的things
        val dirs: Enumeration<URL>
        try {
            dirs = Thread.currentThread().contextClassLoader.getResources(packageDirName)
            // 循环迭代下去
            while (dirs.hasMoreElements()) {
                // 获取下一个元素
                val url: URL = dirs.nextElement()
                // 得到协议的名称
                val protocol: String = url.protocol
                // 如果是以文件的形式保存在服务器上
                if ("file" == protocol) {
                    // 获取包的物理路径
                    val filePath = URLDecoder.decode(url.file, "UTF-8")
                    // 以文件的方式扫描整个包下的文件 并添加到集合中
                    findClassesInPackageByFile(packageName, filePath, recursive, classes)
                } else if ("jar" == protocol) {
                    // 如果是jar包文件
                    // 定义一个JarFile
                    var jar: JarFile
                    try {
                        // 获取jar
                        jar = (url.openConnection() as JarURLConnection).jarFile
                        // 从此jar包 得到一个枚举类
                        val entries: Enumeration<JarEntry> = jar.entries()
                        // 同样的进行循环迭代
                        while (entries.hasMoreElements()) {
                            // 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
                            val entry: JarEntry = entries.nextElement()
                            var name: String = entry.name
                            // 如果是以/开头的
                            if (name[0] == '/') {
                                // 获取后面的字符串
                                name = name.substring(1)
                            }
                            // 如果前半部分和定义的包名相同
                            if (name.startsWith(packageDirName)) {
                                val idx = name.lastIndexOf('/')
                                // 如果以"/"结尾 是一个包
                                if (idx != -1) {
                                    // 获取包名 把"/"替换成"."
                                    packageName = name.substring(0, idx).replace('/', '.')
                                }
                                // 如果可以迭代下去 并且是一个包
                                if (idx != -1 || recursive) {
                                    // 如果是一个.class文件 而且不是目录
                                    if (name.endsWith(".class") && !entry.isDirectory) {
                                        // 去掉后面的".class" 获取真正的类名
                                        val className = name.substring(packageName.length + 1, name.length - 6)
                                        try {
                                            // 添加到classes
                                            classes.add(Class.forName("$packageName.$className"))
                                        } catch (e: ClassNotFoundException) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return classes
    }


    private fun findClassesInPackageByFile(
        packageName: String,
        packagePath: String,
        recursive: Boolean,
        classes: MutableList<Class<*>>
    ) {
        // 获取此包的目录 建立一个File
        val dir = File(packagePath)
        // 如果不存在或者 也不是目录就直接返回
        if (!dir.exists() || !dir.isDirectory) {
            // log.warn("用户定义包名 " + packageName + " 下没有任何文件");
            return
        }
        // 如果存在 就获取包下的所有文件 包括目录
        val dirfiles: Array<File> = dir.listFiles { file ->
            // 自定义过滤规则 如果可以循环(包含子目录) 或则是以.class结尾的文件(编译好的java类文件)
            recursive && file.isDirectory || file.name.endsWith(".class")
        }
        // 循环所有文件
        for (file in dirfiles) {
            // 如果是目录 则继续扫描
            if (file.isDirectory) {
                findClassesInPackageByFile(packageName + "." + file.name, file.absolutePath, recursive, classes)
            } else {
                // 如果是java类文件 去掉后面的.class 只留下类名
                val className: String = file.name.substring(0, file.name.length - 6)
                try {
                    // 添加到集合中去
                    // classes.add(Class.forName(packageName + '.' +
                    // className));
                    // 经过回复同学的提醒，这里用forName有一些不好，会触发static方法，没有使用classLoader的load干净
                    classes.add(Thread.currentThread().contextClassLoader.loadClass("$packageName.$className"))
                } catch (e: ClassNotFoundException) {
                    // log.error("添加用户自定义视图类错误 找不到此类的.class文件");
                    e.printStackTrace()
                }
            }
        }
    }
}