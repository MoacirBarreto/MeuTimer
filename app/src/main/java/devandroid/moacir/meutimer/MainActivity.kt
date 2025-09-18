package devandroid.moacir.meutimer

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import devandroid.moacir.meutimer.databinding.ActivityMainBinding
import java.util.Locale

//import androidx.compose.ui.semantics.text

//### O que este código faz:
//
//1.  **View Binding (`binding`):** Permite chamar `binding.btnIniciar`, `binding.editTextTimer`, etc., de forma segura e eficiente.
//2.  **`onCreate()`:** É onde tudo começa. Ele configura o layout e define os `setOnClickListener` para cada botão.
//3.  **`iniciarOuContinuarTimer()`:**
//*   Verifica se um timer já está rodando.
//*   Se um timer foi pausado (`tempoRestanteEmMs > 0`), ele o retoma de onde parou.
//*   Senão, ele lê o texto do `EditText`, valida, converte de "MM:SS" para milissegundos e chama `iniciarTimer()`.
//*   Ele usa um bloco `try-catch` para evitar que o app quebre se o usuário digitar um formato inválido.
//4.  **`iniciarTimer()`:**
//*   Cria e inicia um `CountDownTimer`, a classe principal do Android para contagens regressivas.
//*   **`onTick()`:** É chamado a cada segundo. Ele atualiza o tempo restante e chama a função para mostrar na tela.
//*   **`onFinish()`:** É chamado quando o tempo acaba. Mostra um `Toast` e zera tudo.
//5.  **`pausarTimer()`:** Cancela o timer atual, mas salva o tempo que ainda faltava na variável `tempoRestanteEmMs`.
//6.  **`zerarTimer()`:** Para o timer, reseta todas as variáveis e limpa a tela.
//7.  **`atualizarInterfaceTimer()`:** Pega o tempo em milissegundos, calcula os minutos e segundos, e formata o texto no `EditText` para sempre mostrar no formato "00:00".
//
//Agora, é só rodar o projeto. Você terá um timer funcional com lógica de iniciar, pausar, continuar e zerar

class MainActivity : AppCompatActivity() {
    // Declaração do View Binding para acessar os componentes do XML de forma segura
    private lateinit var binding: ActivityMainBinding

    // Variável para o nosso cronômetro
    private var countDownTimer: CountDownTimer? = null

    // Variáveis para controlar o estado do timer
    private var tempoRestanteEmMs: Long = 0
    private var timerEstaRodando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Infla o layout usando o View Binding e o define como o conteúdo da tela
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configura os cliques dos botões
        binding.btnIniciar.setOnClickListener {
            iniciarOuContinuarTimer()
        }

        binding.btnPausar.setOnClickListener {
            pausarTimer()
        }

        binding.btnZerar.setOnClickListener {
            zerarTimer()
        }

        // Botões de Minutos
        binding.btnAumentarMinuto.setOnClickListener { alterarTempo(1, 0) }
        binding.btnDiminuirMinuto.setOnClickListener { alterarTempo(-1, 0) }

        // Botões de Segundos (NOVOS)
        binding.btnAumentarSegundo.setOnClickListener { alterarTempo(0, 5) }
        binding.btnDiminuirSegundo.setOnClickListener { alterarTempo(0, -5) }

    }

    private fun alterarTempo(minutosParaAdicionar: Int, segundosParaAdicionar: Int) {

        val tempoAtualTexto = binding.editTextTimer.text.toString()
        var minutosAtuais = 0
        var segundosAtuais = 0

        try {
            if (tempoAtualTexto.isNotEmpty() && tempoAtualTexto.contains(":")) {
                val partes = tempoAtualTexto.split(":")
                minutosAtuais = partes[0].toInt()
                segundosAtuais = partes[1].toInt()
            }
        } catch (e: NumberFormatException) {
            // Ignora o erro e continua com os valores zerados
        }

        // Soma os valores passados como parâmetro
        var novosMinutos = minutosAtuais + minutosParaAdicionar
        var novosSegundos = segundosAtuais + segundosParaAdicionar

        // ---- Lógica para lidar com o "rollover" dos segundos ----
        if (novosSegundos >= 60) {
            novosMinutos += novosSegundos / 60 // Adiciona os minutos extras
            novosSegundos %= 60 // Pega o resto dos segundos
        }

        while (novosSegundos < 0) {
            if (novosMinutos > 0) {
                novosMinutos--
                novosSegundos += 60
            } else {
                // Se não há minutos para "emprestar", trava os segundos em 0
                novosSegundos = 0
            }
        }

        // Garante que os minutos não fiquem negativos
        if (novosMinutos < 0) {
            novosMinutos = 0
        }

        val tempoFormatado =
            String.format(Locale.getDefault(), "%02d:%02d", novosMinutos, novosSegundos)
        binding.editTextTimer.setText(tempoFormatado)

    }

    private fun iniciarOuContinuarTimer() {
        if (timerEstaRodando) {
            // Se já estiver rodando, não faz nada
            return
        }

        val tempoDeEntrada = binding.editTextTimer.text.toString()

        // Se o tempo restante for maior que zero, significa que estamos continuando um timer pausado
        if (tempoRestanteEmMs > 0) {
            iniciarTimer(tempoRestanteEmMs)
        } else {
            // Se não, pegamos o valor do EditText para iniciar um novo timer
            if (tempoDeEntrada.isBlank() || tempoDeEntrada == "00:00") {
                Toast.makeText(this, "Por favor, insira um tempo válido!", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            // Converte a string "MM:SS" para milissegundos
            try {
                val partes = tempoDeEntrada.split(":")
                val minutos = partes[0].toLong()
                val segundos = partes[1].toLong()
                tempoRestanteEmMs = (minutos * 60 + segundos) * 1000
                iniciarTimer(tempoRestanteEmMs)
            } catch (e: Exception) {
                Toast.makeText(this, "Formato de tempo inválido!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun iniciarTimer(milissegundos: Long) {
        countDownTimer = object : CountDownTimer(milissegundos, 1000) {
            // Chamado a cada segundo
            override fun onTick(millisUntilFinished: Long) {
                tempoRestanteEmMs = millisUntilFinished
                atualizarInterfaceTimer()
            }

            // Chamado quando o timer termina
            override fun onFinish() {
                binding.editTextTimer.setText("00:00")
                Toast.makeText(this@MainActivity, "Tempo esgotado!", Toast.LENGTH_LONG).show()
                try {
                    val mediaPlayer = MediaPlayer.create(this@MainActivity, R.raw.alarmbeep)
                    mediaPlayer.start()
                    mediaPlayer?.setOnCompletionListener { player ->
                        player.release()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                zerarTimer()
            }
        }.start()

        timerEstaRodando = true
        // Desabilita o EditText para o usuário não mudar o tempo durante a contagem
        binding.editTextTimer.isEnabled = false
        binding.btnIniciar.text = "Iniciar"
    }

    private fun pausarTimer() {
        if (!timerEstaRodando) return // Se não estiver rodando, não faz nada
        countDownTimer?.cancel()
        timerEstaRodando = false
        // Muda o texto do botão para indicar que o próximo clique será para continuar
        binding.btnIniciar.text = "Continuar"
    }

    private fun zerarTimer() {
        countDownTimer?.cancel()
        tempoRestanteEmMs = 0
        timerEstaRodando = false
        atualizarInterfaceTimer()
        // Reabilita o EditText e limpa o texto
        binding.editTextTimer.isEnabled = true
        binding.btnIniciar.text = "Iniciar" // Garante que o texto seja "Iniciar" = "Iniciar"
    }

    private fun atualizarInterfaceTimer() {
        val minutos = (tempoRestanteEmMs / 1000) / 60
        val segundos = (tempoRestanteEmMs / 1000) % 60
        // Formata o tempo como "MM:SS"
        val tempoFormatado = String.format(Locale.getDefault(), "%02d:%02d", minutos, segundos)
        binding.editTextTimer.setText(tempoFormatado)
    }

    // É uma boa prática cancelar o timer quando o app é destruído para evitar vazamento de memória
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

