package devandroid.moacir.meutimer

import android.content.res.Configuration
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Parcel
import android.os.Parcelable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.transition.TransitionManager
import devandroid.moacir.meutimer.databinding.ActivityMainBinding
import java.util.Locale

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

class MainActivity() : AppCompatActivity(), Parcelable {
    private lateinit var binding: ActivityMainBinding
    private var countDownTimer: CountDownTimer? = null
    private var tempoRestanteEmMs: Long = 0
    private var timerEstaRodando = false
    private var tempoInicialConfiguradoEmMs: Long = 0
    private val portraitConstraints = ConstraintSet()
    private val landscapeConstraints = ConstraintSet()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var vibrator: Vibrator

    constructor(parcel: Parcel) : this() {
        tempoRestanteEmMs = parcel.readLong()
        timerEstaRodando = parcel.readByte() != 0.toByte()
        tempoInicialConfiguradoEmMs = parcel.readLong()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Infla o layout usando o View Binding e o define como o conteúdo da tela
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

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

        binding.btnReiniciar.setOnClickListener { reiniciarTimer() }

        setupConstraints()
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            landscapeConstraints.applyTo(binding.main)
        } else {
            portraitConstraints.applyTo(binding.main)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Anima suavemente a transição entre os layouts
        TransitionManager.beginDelayedTransition(binding.main)

        // Verifica a nova orientação e aplica o conjunto de restrições correto
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            landscapeConstraints.applyTo(binding.main)
        } else {
            portraitConstraints.applyTo(binding.main)
        }
    }

    private fun setupConstraints() {
        // Salva o estado atual (do XML) como o layout de retrato
        portraitConstraints.clone(binding.main)
        // Cria uma cópia para começar a modificar para o layout de paisagem
        landscapeConstraints.clone(binding.main)

        // --- INÍCIO DAS MODIFICAÇÕES PARA O MODO PAISAGEM ---

        // 1. Ocultar o Título e o Relógio para economizar espaço vertical
        landscapeConstraints.setVisibility(R.id.textViewTitulo, ConstraintSet.GONE)
        landscapeConstraints.setVisibility(R.id.textClock, ConstraintSet.GONE)

        // 2. Centralizar verticalmente o EditText e movê-lo um pouco para a esquerda
        landscapeConstraints.connect(
            R.id.editTextTimer,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP
        )
        landscapeConstraints.connect(
            R.id.editTextTimer,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM
        )
        landscapeConstraints.setHorizontalBias(R.id.editTextTimer, 0.4f)

        // 3. Mover os botões de controle (Iniciar, Pausar, Zerar, Reiniciar) para o lado direito
        val chainViews =
            intArrayOf(R.id.btnIniciar, R.id.btnPausar, R.id.btnZerar, R.id.btnReiniciar)
        val chainWeights = floatArrayOf(0f, 0f, 0f, 0f)

        // Limpa as constraints verticais antigas dos botões para evitar conflitos
        landscapeConstraints.clear(R.id.btnIniciar, ConstraintSet.TOP)
        landscapeConstraints.clear(R.id.btnPausar, ConstraintSet.TOP)
        landscapeConstraints.clear(R.id.btnZerar, ConstraintSet.TOP)
        landscapeConstraints.clear(R.id.btnReiniciar, ConstraintSet.TOP)

        // Cria uma corrente vertical com os botões, "empacotando-os" no centro
        landscapeConstraints.createVerticalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.TOP,
            ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM,
            chainViews, chainWeights, ConstraintSet.CHAIN_PACKED
        )
        // Alinha a corrente de botões à direita do EditText
        landscapeConstraints.connect(
            R.id.btnIniciar,
            ConstraintSet.START,
            R.id.editTextTimer,
            ConstraintSet.END,
            32
        )
        landscapeConstraints.connect(
            R.id.btnIniciar,
            ConstraintSet.END,
            ConstraintSet.PARENT_ID,
            ConstraintSet.END,
            32
        )
        landscapeConstraints.connect(
            R.id.btnPausar,
            ConstraintSet.START,
            R.id.btnIniciar,
            ConstraintSet.START
        )
        landscapeConstraints.connect(
            R.id.btnPausar,
            ConstraintSet.END,
            R.id.btnIniciar,
            ConstraintSet.END
        )
        landscapeConstraints.connect(
            R.id.btnZerar,
            ConstraintSet.START,
            R.id.btnIniciar,
            ConstraintSet.START
        )
        landscapeConstraints.connect(
            R.id.btnZerar,
            ConstraintSet.END,
            R.id.btnIniciar,
            ConstraintSet.END
        )
        landscapeConstraints.connect(
            R.id.btnReiniciar,
            ConstraintSet.START,
            R.id.btnIniciar,
            ConstraintSet.START
        )
        landscapeConstraints.connect(
            R.id.btnReiniciar,
            ConstraintSet.END,
            R.id.btnIniciar,
            ConstraintSet.END
        )

        // Garante que os botões Pausar e Zerar tenham a mesma largura que o Iniciar
        landscapeConstraints.constrainWidth(R.id.btnPausar, ConstraintSet.MATCH_CONSTRAINT)
        landscapeConstraints.constrainWidth(R.id.btnZerar, ConstraintSet.MATCH_CONSTRAINT)
        landscapeConstraints.constrainWidth(R.id.btnReiniciar, ConstraintSet.MATCH_CONSTRAINT)

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
                val tempoTotalMs = (minutos * 60 + segundos) * 1000

                tempoInicialConfiguradoEmMs = tempoTotalMs // Salva o tempo configurado
                tempoRestanteEmMs = tempoTotalMs

                iniciarTimer(tempoRestanteEmMs)
            } catch (e: Exception) {
                Toast.makeText(this, "Formato de tempo inválido!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun reiniciarTimer() {
        // Para o som e a vibração, se estiverem ativos
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
        vibrator.cancel()

        // Cancela qualquer timer em andamento
        countDownTimer?.cancel()
        timerEstaRodando = false

        if (tempoInicialConfiguradoEmMs > 0) {
            tempoRestanteEmMs =
                tempoInicialConfiguradoEmMs // Restaura o tempo restante para o valor inicial salvo
            atualizarInterfaceTimer() // Mostra o tempo inicial no visor
            iniciarTimer(tempoRestanteEmMs) // Começa a contagem regressiva novamente
            binding.editTextTimer.isEnabled = false
            binding.btnIniciar.text = getString(R.string.botao_iniciar)
        } else {
            // Se nenhum tempo foi configurado antes, apenas zera (comportamento do botão Zerar)
            zerarTimer()
            Toast.makeText(this, "Nenhum tempo anterior para reiniciar.", Toast.LENGTH_SHORT).show()
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
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer.create(this@MainActivity, R.raw.alarmbeep)
                    mediaPlayer?.isLooping = true // Opcional: faz o alarme repetir até ser parado
                    mediaPlayer?.start()
                    val pattern = longArrayOf(0, 1000, 500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                pattern,
                                0
                            )
                        ) // 0 para repetir
                    } else {
                        // Método antigo para APIs mais velhas
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, 0)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
                timerEstaRodando = false
                tempoRestanteEmMs = 0
                binding.editTextTimer.isEnabled = true
                binding.btnIniciar.text = getString(R.string.botao_iniciar)
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
        vibrator.cancel()

        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release() // Libera os recursos do MediaPlayer
            mediaPlayer = null // Limpa a referência
        }

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

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        vibrator.cancel()
        // Garante que o som pare e os recursos sejam liberados ao fechar o app
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        // Verifica se a tecla pressionada foi a de "Aumentar Volume"
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {

            // << MODIFICAÇÃO >>
            // Chama a função para reiniciar o timer
            reiniciarTimer()

            // Opcional: Mostra uma mensagem para confirmar a ação
            Toast.makeText(this, "Timer reiniciado pelo botão de volume", Toast.LENGTH_SHORT).show()

            // Retorna 'true' para indicar que o evento foi consumido.
            // Isso impede que o sistema também mostre a barra de volume.
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            zerarTimer()
            Toast.makeText(this, "Timer zerado pelo botão de volume", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(tempoRestanteEmMs)
        parcel.writeByte(if (timerEstaRodando) 1 else 0)
        parcel.writeLong(tempoInicialConfiguradoEmMs)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MainActivity> {
        override fun createFromParcel(parcel: Parcel): MainActivity {
            return MainActivity(parcel)
        }

        override fun newArray(size: Int): Array<MainActivity?> {
            return arrayOfNulls(size)
        }
    }

}
