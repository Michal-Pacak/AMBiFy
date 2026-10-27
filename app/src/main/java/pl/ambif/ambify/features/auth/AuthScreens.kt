package pl.ambif.ambify.features.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.shared.AccountRules
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Tier
import pl.ambif.ambify.ui.branding.BrandLogo
import pl.ambif.ambify.ui.icons.AppIcons

internal object AuthColors {
    val Gold = Color(0xFFD4A93C)
    val OnGold = Color(0xFF1A1A1A)
    val Muted = Color(0xFFC9C9CF)
    val Field = Color(0xFF1C1C1F)
    val FieldBorder = Color(0xFF5A5A60)
    val Tonal = Color(0xFF3B4258)
    val OnTonal = Color(0xFFC9D3F2)
    val Error = Color(0xFFFF8A80)
}

/** A new account without a community; founding one or joining with a code comes next ([StartScreen]). */
@Composable
fun RegisterScreen(onSignedIn: () -> Unit, onBack: () -> Unit) {
    val session = LocalAppSession.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        checkPasswords(password, repeat)
        session.signIn(session.api.register(RegisterRequest(null, name, email, password, session.deviceName)))
        onSignedIn()
    }

    AuthLayout(top = 16.dp) {
        BackButton(onBack)
        AuthHeader("Załóż konto", "Społeczność założysz albo dołączysz do niej z kodem w następnym kroku.")
        AuthField("Twoje imię", name, { name = it }, capitalize = true)
        AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
        PasswordField("Hasło (min. ${AccountRules.MIN_PASSWORD_LENGTH} znaków)", password, { password = it })
        PasswordField("Powtórz hasło", repeat, { repeat = it }, last = true, onDone = submit.run)
        SubmitButton("Załóż konto", submit)
    }
}

/** Signed in without a community: found the first one (free) or join one with an invitation code. */
@Composable
fun StartScreen(name: String, onReady: () -> Unit) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    var communityName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val found = rememberSubmit {
        session.createCommunity(communityName)
        onReady()
    }
    val join = rememberSubmit {
        session.joinCommunity(code)
        onReady()
    }

    AuthLayout {
        AuthHeader("Witaj, $name!", "Nie należysz jeszcze do żadnej społeczności. Załóż własną albo dołącz do istniejącej z kodem zaproszenia.")
        StartChoice(
            title = "Załóż społeczność",
            text = "Zostaniesz jej Ownerem. Pierwsza społeczność jest za darmo, z modułami " +
                ModuleId.entries.filter { it.tier == Tier.BASIC }.joinToString(", ") { it.label } + ".",
            primary = true,
            onClick = { choice = FOUND },
        )
        if (choice == FOUND) {
            AuthField("Nazwa społeczności", communityName, { communityName = it }, capitalize = true, last = true, onDone = found.run)
            SubmitButton("Załóż społeczność", found)
        }
        StartChoice(
            title = "Mam kod zaproszenia",
            text = "Kod dostajesz od Ownera lub Admina społeczności. Jest jednorazowy i ważny ${AccountRules.INVITATION_DAYS} dni.",
            primary = false,
            onClick = { choice = JOIN },
        )
        if (choice == JOIN) {
            CodeField("Kod zaproszenia", code, { code = it })
            SubmitButton("Dołącz do społeczności", join)
        }
        TextButton(onClick = { scope.launch { session.logout() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Wyloguj", color = AuthColors.Muted, fontSize = 15.sp)
        }
    }
}

private const val FOUND = "found"
private const val JOIN = "join"

@Composable
private fun StartChoice(title: String, text: String, primary: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(text, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AuthColors.Gold, contentColor = AuthColors.OnGold),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, AuthColors.Gold),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthColors.Gold),
        ) { content() }
    }
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit, onCreateAccount: () -> Unit, onForgotPassword: () -> Unit, notice: String? = null) {
    val session = LocalAppSession.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        session.signIn(session.api.login(LoginRequest(email, password, session.deviceName)))
        onSignedIn()
    }

    AuthLayout(top = 40.dp) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLogo(modifier = Modifier.size(76.dp), animated = false, cropToLogo = true)
            Text("AMBiFy", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        notice?.let { Text(it, color = AuthColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
        PasswordField("Hasło", password, { password = it }, last = true, onDone = submit.run)
        SubmitButton("Zaloguj", submit)
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Nie pamiętam hasła", color = AuthColors.Gold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (BuildConfig.TEST_LOGIN) TestLogin(onSignedIn)
        Text("Nie masz konta?", color = AuthColors.Muted, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedButton(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            border = BorderStroke(1.5.dp, AuthColors.Gold),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthColors.Gold),
        ) {
            Text("Załóż konto", fontSize = 16.sp)
        }
        Text(
            "Konto zakładasz bez kodu. Społeczność wybierasz w następnym kroku.",
            color = AuthColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun JoinScreen(onSignedIn: () -> Unit, onBack: () -> Unit) {
    val session = LocalAppSession.current
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        checkPasswords(password, repeat)
        session.signIn(session.api.register(RegisterRequest(code, name, email, password, session.deviceName)))
        onSignedIn()
    }

    AuthLayout(top = 16.dp) {
        BackButton(onBack)
        AuthHeader("Dołącz do społeczności", "Kod dostaniesz od Ownera lub Admina. Jest jednorazowy i ważny ${AccountRules.INVITATION_DAYS} dni.")
        CodeField("Kod zaproszenia", code, { code = it })
        AuthField("Twoje imię", name, { name = it }, capitalize = true)
        AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
        PasswordField("Hasło (min. ${AccountRules.MIN_PASSWORD_LENGTH} znaków)", password, { password = it })
        PasswordField("Powtórz hasło", repeat, { repeat = it }, last = true, onDone = submit.run)
        SubmitButton("Dołącz do społeczności", submit)
    }
}

/** "I forgot my password": the code comes from an Owner or Admin (Konto → Społeczność → ⋮ → Resetuj hasło), an Owner's from the Platform Admin. */
@Composable
fun ResetPasswordScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val session = LocalAppSession.current
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        checkPasswords(password, repeat)
        session.api.resetPassword(ResetPasswordRequest(email, code, password))
        onDone()
    }

    AuthLayout(top = 16.dp) {
        BackButton(onBack)
        AuthHeader(
            "Nowe hasło",
            "Kod resetu dostaniesz od Ownera lub Admina swojej społeczności (Konto → Społeczność → ⋮ → Resetuj hasło). " +
                "Owner dostaje kod od Platform Admina. Kod jest ważny 24 godziny.",
        )
        AuthField("E-mail", email, { email = it }, keyboardType = KeyboardType.Email)
        CodeField("Kod resetu", code, { code = it })
        PasswordField("Nowe hasło (min. ${AccountRules.MIN_PASSWORD_LENGTH} znaków)", password, { password = it })
        PasswordField("Powtórz hasło", repeat, { repeat = it }, last = true, onDone = submit.run)
        SubmitButton("Ustaw nowe hasło", submit)
    }
}

/** AMBiFy TEST only: one tap signs in with a demo account of the chosen role (test server data). */
@Composable
private fun TestLogin(onSignedIn: () -> Unit) {
    val session = LocalAppSession.current
    var chosen by remember { mutableStateOf<String?>(null) }
    val submit = rememberSubmit {
        session.signIn(session.api.login(LoginRequest(chosen!!, "test1234", session.deviceName)))
        onSignedIn()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Szybkie logowanie (test)", color = AuthColors.Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        listOf(
            "Owner" to "owner@test.ambify",
            "Admin" to "admin@test.ambify",
            "User" to "user@test.ambify",
            "Platform Admin" to "platform@test.ambify",
        ).forEach { (role, email) ->
            FilledTonalButton(
                onClick = {
                    chosen = email
                    submit.run()
                },
                enabled = !submit.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AuthColors.Tonal, contentColor = AuthColors.OnTonal),
            ) { Text("Wejdź jako $role", fontSize = 16.sp) }
        }
        submit.error?.let { Text(it, color = AuthColors.Error, fontSize = 14.sp) }
    }
}

// --- building blocks ---

internal class Submit(val busy: Boolean, val error: String?, val run: () -> Unit)

/** Runs [action] once at a time and turns a failure into a message under the button. */
@Composable
internal fun rememberSubmit(action: suspend () -> Unit): Submit {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    return Submit(busy, error) {
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    action()
                } catch (e: ServerError) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
    }
}

internal fun checkPasswords(password: String, repeat: String) {
    if (password != repeat) throw ServerError("mismatch", "Hasła nie są takie same.")
}

@Composable
private fun AuthLayout(top: androidx.compose.ui.unit.Dp = 56.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = top, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun AuthHeader(title: String, subtitle: String, centered: Boolean = false, logo: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (logo) BrandLogo(modifier = Modifier.size(76.dp), animated = false, cropToLogo = true)
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = AuthColors.Muted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
        Icon(AppIcons.Back, contentDescription = "Wróć", tint = Color.White)
    }
}

@Composable
internal fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp),
    borderColor: Color = AuthColors.FieldBorder,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = AuthColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = textStyle,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = visualTransformation,
            trailingIcon = trailing,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = AuthColors.Field,
                unfocusedContainerColor = AuthColors.Field,
                focusedBorderColor = AuthColors.Gold,
                unfocusedBorderColor = borderColor,
                cursorColor = AuthColors.Gold,
            ),
        )
    }
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit, last: Boolean = false, onDone: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    AuthField(
        label, value, onValueChange,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            // Not focusable: the keyboard's "Next" must go to the next field, not to this button.
            IconButton(onClick = { visible = !visible }, modifier = Modifier.focusProperties { canFocus = false }) {
                Icon(
                    if (visible) AppIcons.EyeOff else AppIcons.Eye,
                    contentDescription = if (visible) "Ukryj hasło" else "Pokaż hasło",
                    tint = AuthColors.Muted,
                )
            }
        },
        last = last,
        onDone = onDone,
    )
}

@Composable
private fun CodeField(label: String, value: String, onValueChange: (String) -> Unit) {
    AuthField(
        label, value, { onValueChange(it.uppercase().filter(Char::isLetterOrDigit).take(8)) },
        textStyle = TextStyle(fontSize = 22.sp, fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, textAlign = TextAlign.Center, color = Color.White),
        borderColor = AuthColors.Gold,
    )
}

@Composable
private fun SubmitButton(text: String, submit: Submit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        submit.error?.let { Text(it, color = AuthColors.Error, fontSize = 15.sp) }
        Button(
            onClick = submit.run,
            enabled = !submit.busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuthColors.Gold,
                contentColor = AuthColors.OnGold,
                disabledContainerColor = AuthColors.Gold.copy(alpha = 0.5f),
                disabledContentColor = AuthColors.OnGold,
            ),
        ) {
            if (submit.busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AuthColors.OnGold, strokeWidth = 2.dp)
            } else {
                Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
