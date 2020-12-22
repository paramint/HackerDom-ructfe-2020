import io.javalin.Javalin
import io.javalin.http.Context
import kotlinx.html.*
import java.io.File
import java.nio.file.Path


fun App.getAuthenticatedUser(ctx: Context): String? {
    val login = ctx.cookie("login") ?: run { return null }
    val secret = ctx.cookie("secret") ?: run { return null }
    if (sessionManager.validate(login, secret)) {
        return login
    }
    return null
}


fun App.checkFilesAccess(ctx: Context): File? {
    val authenticatedUser = getAuthenticatedUser(ctx)

    if (authenticatedUser == null) {
        ctx.status(403)
        return null
    }

    val authenticatedUserDir = File(STORAGE_PATH).resolve(authenticatedUser)
    if (!authenticatedUserDir.exists()) {
        authenticatedUserDir.mkdirs()
    }

    if (!ctx.path().startsWith(OtherConstants.FILES_PATH)) {
        ctx.status(400)
        return null
    }

    return authenticatedUserDir
}


fun App.addFilesHandler(): Javalin = javalin.get("/files/*") { ctx ->
    val authenticatedUserDir = checkFilesAccess(ctx) ?: return@get

    val lastPath = ctx.path().substring(OtherConstants.FILES_PATH.length)

    val file = authenticatedUserDir.resolve(lastPath)
    if (!file.exists()) {
        ctx.status(400)
        return@get
    }

    if (file.isFile) {
        ctx.result(file.readBytes())
        return@get
    }

    if (file.isDirectory) {
        ctx.json((file.list() ?: emptyArray()) as Array<String>)
        return@get
    }
}


fun App.addUploadFilesHandler(): Javalin = javalin.post("/files/*") { ctx ->
    val authenticatedUserDir = checkFilesAccess(ctx) ?: return@post
    val newFile = ctx.uploadedFile("file") ?: return@post  // TODO: Use try-catch

    val lastPath = ctx.path().substring(OtherConstants.FILES_PATH.length)

    val file = authenticatedUserDir.resolve(lastPath)
    if (file.exists()) {
        ctx.status(400)
        return@post
    }

    file.writeBytes(newFile.content.readAllBytes())
}


fun App.addIndexHandler(): Javalin = javalin.get("/") { ctx ->
    ctx.redirect("/main")
}


fun App.addMainHandler(): Javalin = javalin.get("/main") { ctx ->
    val authenticatedUser = getAuthenticatedUser(ctx)

    if (authenticatedUser == null) {
        ctx.withHtml {
            body {
                p {
                    +"You need to authorize "
                    a(Endpoints.REGISTER_PAGE) { +"here" }
                }
            }
        }
        ctx.contentType("text/html")
        return@get
    }

    ctx.withHtml {
        head {
            script {
                unsafe {
                    +"let path = [];"
                    +"let username = \"$authenticatedUser\";"
                    +"let cmd_prompt = \"$authenticatedUser@keeper:~ \";"
                }
            }
            script(null, "https://code.jquery.com/jquery-3.2.1.min.js") {}
            script(null, "https://cdnjs.cloudflare.com/ajax/libs/jquery.terminal/2.20.1/js/jquery.terminal.min.js") {}
            script(null, "/js/main.js") {}
            link(
                href = "https://cdnjs.cloudflare.com/ajax/libs/jquery.terminal/2.20.1/css/jquery.terminal.min.css",
                rel = "stylesheet"
            )
            link(href = "/css/main.css", rel = "stylesheet")
        }
        body {
            div { id = "term_demo" }
//            div {
//                id = "download"
////                style = "display: none;"
//                a(href = "/files/file") {
//                    id = "dlink"
//                    +"Download"
//                }
//            }
            input(type = InputType.file) { id = "ufile" }
        }
    }
    ctx.contentType("text/html")
}


fun FlowOrInteractiveOrPhrasingContent.loginAndPassword() {
    label { text("Login") }
    input(type = InputType.text, name = "login")
    br
    label { text("Password") }
    input(type = InputType.password, name = "password")
    br
}


fun App.addRegisterPageHandler(): Javalin = javalin.get(Endpoints.REGISTER_PAGE) { ctx ->
    getAuthenticatedUser(ctx)?.let {
        ctx.redirect("/")
        return@get
    }

    ctx.withHtml {
        body {
            form(action = Endpoints.REGISTER, method = FormMethod.post) {
                loginAndPassword()
                button { text("Register") }
            }
            a(href = Endpoints.LOGIN_PAGE) { +"Go to login page" }
        }
    }
    ctx.contentType("text/html")
}


fun App.addLoginPageHandler(): Javalin = javalin.get(Endpoints.LOGIN_PAGE) { ctx ->
    getAuthenticatedUser(ctx)?.let {
        ctx.redirect("/")
        return@get
    }

    ctx.withHtml {
        body {
            form(action = Endpoints.LOGIN, method = FormMethod.post) {
                loginAndPassword()
                button { text("Login") }
            }
            a(href = Endpoints.REGISTER_PAGE) { +"Go to register page" }
        }
    }
    ctx.contentType("text/html")
}


fun Context.getFormParamOrBadStatus(name: String): String? {
    return formParam(name) ?: run {
        status(400)
        null
    }
}


fun App.addLoginHandler(): Javalin = javalin.post(Endpoints.LOGIN) { ctx ->
    val login = ctx.getFormParamOrBadStatus("login") ?: run { return@post }
    val password = ctx.getFormParamOrBadStatus("password") ?: run { return@post }
    if (userStorage.isValid(login, password)) {
        authenticate(ctx, login)
    } else {
        ctx.status(403)
    }
}


fun App.authenticate(ctx: Context, login: String) {
    ctx.clearCookieStore()
    val secret = sessionManager.create(login)
    ctx.cookie("login", login)
    ctx.cookie("secret", secret)
    ctx.redirect("/main")
}


fun App.addRegisterHandler(): Javalin = javalin.post(Endpoints.REGISTER) { ctx ->
    val login = ctx.getFormParamOrBadStatus("login") ?: run { return@post }
    val password = ctx.getFormParamOrBadStatus("password") ?: run { return@post }

    if (userStorage.exists(login)) {
        ctx.status(400)
        return@post
    }

    userStorage.create(login, password)
    authenticate(ctx, login)
}

fun App.addUploadHandler() {
    //    app.javalin.post("/upload") { ctx ->
//        ctx.uploadedFiles("files").forEach { (contentType, content, name, extension) ->
//            println(contentType)
//            println(content)
//            println(name)
//            println(extension)
//        }
//    }
}
