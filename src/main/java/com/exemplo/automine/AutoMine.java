package com.exemplo.automine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * AutoMine 1.8.9 - mineracao automatica com o SEU player (mod de cliente).
 *
 * Comandos (funcionam em qualquer servidor, sao processados no cliente):
 *   /automine                -> liga/desliga
 *   /automine diamante       -> liga so para um minerio
 *                               (tudo, diamante, ferro, ouro, carvao, redstone, lapis, esmeralda, quartzo)
 *                               Por padrao so quebra MINERIOS (blocos compactos ficam de fora).
 *                               /automine incluirblocos on  -> passa a quebrar tambem os blocos
 *                               compactos (diamante, ferro, ouro, redstone, lapis, esmeralda);
 *                               "blocos" quebra so os blocos compactos (sem minerios);
 *                               "blocolapis" quebra so o bloco de lapis
 *   /automine tunel [minerio] -> modo tunel: cava em linha reta (quebra tudo na frente)
 *   /automine escavar        -> quebra TODOS os blocos da area demarcada (pos1/pos2), qualquer tipo,
 *                               de cima pra baixo. Fique dentro da area, de preferencia no ponto mais
 *                               alto. Pula bedrock/inquebraveis e blocos encostados em agua/lava.
 *                               Desliga sozinho quando a area fica limpa. (multi nao vale aqui)
 *   /automine trecho 30      -> segundos andando numa direcao antes de trocar (padrao 30)
 *   /automine raio 64        -> distancia maxima do ponto onde voce ligou (padrao 64)
 *   /automine pos1           -> marca o 1o canto da area (onde voce esta parado)
 *   /automine pos2           -> marca o canto oposto; a area liga sozinha
 *                               (Y conta: pos1 no fundo e pos2 no alto, ou vice-versa)
 *   /automine area on|off|limpar -> liga, desliga ou apaga a area
 *                               Com area ligada ele so mina e so anda dentro dela.
 *   Dropar inventario: botao "Dropar tudo" na tela do inventario, tecla K (muda em Controles)
 *                       ou /automine dropar (tudo) / /automine dropar mochila (mantem a hotbar).
 *                       Nao dropa armadura. Dropa alguns slots por tick.
 *   Ligar/desligar sem digitar: botao "Ligar AutoMine" no menu de pausa (Esc) e botao "Ligar mod"
 *           na tela do inventario; tecla M tambem. Aparece um aviso verde na tela enquanto esta ligado.
 *   Ver players por perto: tecla P ou botao "Ver players" no menu de pausa (Esc) liga/desliga
 *           a lista (nome + distancia) e as linhas ate cada player (aparecem atraves das paredes).
 *           /automine players on|off|lista|linhas|alcance <10-300>  (padrao 64 blocos)
 *           So mostra quem o seu jogo ja carregou (o servidor nao manda players muito longe).
 *   Vender: botao "Vender" na tela do inventario, tecla L ou /automine vender -> manda /vender.
 *           /automine vendermodo abrir -> em vez de enviar, so abre o chat com /vender digitado.
 *   /automine multi 3        -> tenta quebrar ate 3 blocos de uma vez (qualquer numero de 1 a 500; 1 = desligado).
 *                               So conta o que estiver visivel e ao alcance (4 blocos).
 *                               Manda os pedidos de quebra todos juntos. So funciona de verdade em
 *                               criativo ou em servidor que nao valida; em survival o servidor
 *                               costuma ignorar os extras e pode dar kick/bloco fantasma.
 *   /automine velocidade 6   -> 1 = bem lento (camera e quebra calmas) ... 6 = maximo (padrao 6)
 *   /automine parar          -> desliga
 * Tecla M (configuravel em Controles) tambem liga/desliga.
 *
 * MODO PADRAO (andando): o player fica andando em direcoes aleatorias, desviando de
 * buracos, liquidos e paredes (pula degraus de 1 bloco). Quando ve um minerio, vai ate
 * ele, quebra, pega o drop e continua andando. Nao quebra pedra comum.
 * Para sozinho se cair lava por perto ou se a vida ficar baixa.
 */
@Mod(modid = AutoMine.MODID, name = "AutoMine", version = "1.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class AutoMine {
    public static final String MODID = "automine";

    private static final double ALCANCE = 4.0;
    private static final int RAIO_BUSCA = 4;
    private static final double ALCANCE_LONGE = 10.0;
    private static final int RAIO_LONGE = 10;
    private static final float VIDA_MINIMA = 8.0f;
    private static final int INTERVALO_PULO = 80; // pula a cada 80 ticks = 4 segundos

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rng = new Random();
    private KeyBinding teclaToggle;
    private boolean ativo = false;
    private boolean modoTunel = false;
    private EnumFacing direcao = EnumFacing.NORTH;
    private final Set<Block> minerios = new HashSet<Block>();
    private boolean incluirBlocos = false; // minera os blocos compactos? (padrao: nao)
    private String filtroAtual = "tudo";
    private boolean modoEscavar = false;   // quebra TODOS os blocos da area demarcada
    private BlockPos ultimoAlvoEscavar = null;

    // estado do modo andando
    private float yawAlvo = 0.0f;
    private int ticksAteTrocar = 0;
    private int ticksVirando = 0;
    private int ticksVarredura = 0;
    private int ticksParado = 0;
    private int coletaTicks = 0;
    private BlockPos alvoLonge = null;
    private BlockPos alvoMinerado = null;
    private BlockPos coletaPos = null;
    private double ultX = 0.0;
    private double ultZ = 0.0;
    private int ticksPuloAuto = 0;
    private boolean puloAuto = false;
    private int trechoSeg = 30;        // segundos andando numa direcao antes de trocar
    private int raioPasseio = 64;      // distancia maxima do ponto onde ligou
    private BlockPos origem = null;

    // velocidade (1 = bem lento ... 5 = rapido): giro da camera por tick,
    // espera antes de comecar a bater e pausa entre um bloco e o proximo
    // nivel 6 = maximo: camera gira instantaneo, sem espera, sem pausa (como na primeira versao)
    private static final float[] GIRO = {4.0f, 6.0f, 9.0f, 14.0f, 25.0f, 180.0f};
    private static final int[] ESPERA_MIRA = {14, 10, 6, 3, 0, 0};
    private static final int[] PAUSA_BLOCO = {20, 14, 8, 4, 0, 0};
    private int nivelVel = 6;
    private int esperaMira = 0;
    private int pausaTicks = 0;

    // dropar inventario: um botao na tela do inventario, a tecla K e /automine dropar
    private static final int BTN_DROPAR = 9001;
    private static final int DROP_POR_TICK = 3;
    private static final int BTN_VENDER = 9002;
    private static final int BTN_TOGGLE = 9003;
    private static final int BTN_PLAYERS = 9004;

    // ver players por perto: lista na tela + linhas ate cada um (aparecem atraves das paredes)
    private boolean listaPlayers = false;
    private boolean linhasPlayers = false;
    private int alcancePlayers = 64;
    private KeyBinding teclaPlayers;
    private KeyBinding teclaVender;
    private boolean venderEnviar = true; // true = envia /vender direto; false = so abre o chat com o texto
    private KeyBinding teclaDropar;
    private final ArrayDeque<Integer> filaDrop = new ArrayDeque<Integer>();

    // multi-quebra: manda pedidos de quebra de ate (multi - 1) blocos extras junto com o principal
    private static final int MULTI_MAX = 500;      // teto so pra evitar digitar errado
    private static final int ENVIOS_POR_TICK = 5;  // quantos pedidos novos manda por tick (evita flood no servidor)
    private int multi = 1; // 1 = desligado
    private final List<Pendente> pendentes = new ArrayList<Pendente>();
    private final Map<BlockPos, Integer> tentativas = new HashMap<BlockPos, Integer>();

    private static class Pendente {
        final BlockPos pos;
        final EnumFacing face;
        int ticks;

        Pendente(BlockPos pos, EnumFacing face, int ticks) {
            this.pos = pos;
            this.face = face;
            this.ticks = ticks;
        }
    }

    private static class Alvo {
        final BlockPos pos;
        final EnumFacing face;
        final double dist;

        Alvo(BlockPos pos, EnumFacing face, double dist) {
            this.pos = pos;
            this.face = face;
            this.dist = dist;
        }
    }

    // area demarcada: so mina e so anda dentro dela
    private BlockPos pos1 = null;
    private BlockPos pos2 = null;
    private boolean usarArea = false;
    private int aMinX, aMaxX, aMinY, aMaxY, aMinZ, aMaxZ;

    private void atualizarArea() {
        aMinX = Math.min(pos1.getX(), pos2.getX());
        aMaxX = Math.max(pos1.getX(), pos2.getX());
        aMinY = Math.min(pos1.getY(), pos2.getY());
        aMaxY = Math.max(pos1.getY(), pos2.getY());
        aMinZ = Math.min(pos1.getZ(), pos2.getZ());
        aMaxZ = Math.max(pos1.getZ(), pos2.getZ());
    }

    private boolean dentroArea(BlockPos b) {
        return b.getX() >= aMinX && b.getX() <= aMaxX
                && b.getY() >= aMinY && b.getY() <= aMaxY
                && b.getZ() >= aMinZ && b.getZ() <= aMaxZ;
    }

    private boolean dentroXZ(double x, double z) {
        return x >= aMinX && x < aMaxX + 1 && z >= aMinZ && z < aMaxZ + 1;
    }

    private BlockPos centroArea() {
        return new BlockPos((aMinX + aMaxX) / 2, (aMinY + aMaxY) / 2, (aMinZ + aMaxZ) / 2);
    }

    private String descricaoArea() {
        if (pos1 == null || pos2 == null) return "nao definida";
        return "X " + aMinX + " a " + aMaxX + ", Y " + aMinY + " a " + aMaxY
                + ", Z " + aMinZ + " a " + aMaxZ + (usarArea ? " (LIGADA)" : " (desligada)");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        teclaToggle = new KeyBinding("Ligar/desligar AutoMine", Keyboard.KEY_M, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaToggle);
        teclaDropar = new KeyBinding("Dropar inventario inteiro", Keyboard.KEY_K, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaDropar);
        teclaVender = new KeyBinding("Vender (/vender)", Keyboard.KEY_L, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaVender);
        teclaPlayers = new KeyBinding("Ver players por perto", Keyboard.KEY_P, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaPlayers);
        ClientCommandHandler.instance.registerCommand(new CommandAutoMine());
        MinecraftForge.EVENT_BUS.register(this);
        definirFiltro("tudo");
    }

    // ---------------------------------------------------------------- filtro

    private boolean definirFiltro(String nome) {
        Set<Block> novo = new HashSet<Block>();
        switch (nome) {
            case "diamante": novo.add(Blocks.diamond_ore); novo.add(Blocks.diamond_block); break;
            case "ferro": novo.add(Blocks.iron_ore); novo.add(Blocks.iron_block); break;
            case "ouro": novo.add(Blocks.gold_ore); novo.add(Blocks.gold_block); break;
            case "carvao": novo.add(Blocks.coal_ore); break;
            case "redstone":
                novo.add(Blocks.redstone_ore); novo.add(Blocks.lit_redstone_ore);
                novo.add(Blocks.redstone_block);
                break;
            case "lapis": novo.add(Blocks.lapis_ore); novo.add(Blocks.lapis_block); break;
            case "esmeralda": novo.add(Blocks.emerald_ore); novo.add(Blocks.emerald_block); break;
            case "quartzo": novo.add(Blocks.quartz_ore); break;
            case "blocolapis": novo.add(Blocks.lapis_block); break;
            case "blocos": // so os blocos compactos, sem os minerios
                novo.add(Blocks.diamond_block); novo.add(Blocks.iron_block); novo.add(Blocks.gold_block);
                novo.add(Blocks.redstone_block); novo.add(Blocks.lapis_block); novo.add(Blocks.emerald_block);
                break;
            case "tudo":
                novo.add(Blocks.diamond_ore); novo.add(Blocks.iron_ore); novo.add(Blocks.gold_ore);
                novo.add(Blocks.coal_ore); novo.add(Blocks.redstone_ore); novo.add(Blocks.lit_redstone_ore);
                novo.add(Blocks.lapis_ore); novo.add(Blocks.emerald_ore); novo.add(Blocks.quartz_ore);
                novo.add(Blocks.diamond_block); novo.add(Blocks.iron_block); novo.add(Blocks.gold_block);
                novo.add(Blocks.redstone_block); novo.add(Blocks.lapis_block); novo.add(Blocks.emerald_block);
                break;
            default: return false;
        }
        // blocos compactos (diamante, ferro, ouro, redstone, lapis, esmeralda) ficam de fora,
        // a menos que o usuario ligue com /automine incluirblocos on (ou use o filtro "blocos")
        if (!incluirBlocos && !nome.equals("blocos") && !nome.equals("blocolapis")) {
            novo.remove(Blocks.diamond_block);
            novo.remove(Blocks.iron_block);
            novo.remove(Blocks.gold_block);
            novo.remove(Blocks.redstone_block);
            novo.remove(Blocks.lapis_block);
            novo.remove(Blocks.emerald_block);
        }
        filtroAtual = nome;
        minerios.clear();
        minerios.addAll(novo);
        return true;
    }

    // ---------------------------------------------------------- liga/desliga

    private void ligar() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        direcao = p.getHorizontalFacing();
        yawAlvo = p.rotationYaw;
        sortearTrecho();
        origem = new BlockPos(p.posX, p.posY, p.posZ);
        ticksVirando = 0;
        ticksVarredura = 0;
        ticksParado = 0;
        coletaTicks = 0;
        esperaMira = 0;
        pausaTicks = 0;
        alvoLonge = null;
        alvoMinerado = null;
        coletaPos = null;
        ultX = p.posX;
        ultZ = p.posZ;
        ativo = true;
        ultimoAlvoEscavar = null;
        msg("AutoMine LIGADO (" + (modoEscavar ? "escavando a area"
                : (modoTunel ? "tunel, direcao " + direcao.getName() : "andando")) + ")");
        if (usarArea) {
            msg("Area: " + descricaoArea());
            if (!dentroXZ(p.posX, p.posZ)) msg("Voce esta fora da area, vou andar ate ela.");
        }
    }

    private void desligar(String motivo) {
        ativo = false;
        cancelarPendentes();
        soltarTeclas();
        msg(motivo);
    }

    private void msg(String texto) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[AutoMine] " + texto));
        }
    }

    private void soltarTeclas() {
        atacar(false);
        andar(false);
        pular(false);
    }

    private void atacar(boolean on) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), on);
    }

    private void andar(boolean on) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), on);
    }

    private void pular(boolean on) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), on);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;

        if (mc.thePlayer == null || mc.theWorld == null) {
            ativo = false;
            pendentes.clear();
            tentativas.clear();
            return;
        }

        if (teclaDropar.isPressed()) droparInventario(true);
        if (teclaVender.isPressed()) vender();
        if (teclaPlayers.isPressed()) {
            alternarPlayers();
            msg("Ver players: " + (listaPlayers ? "LIGADO" : "desligado"));
        }
        processarFilaDrop();

        if (teclaToggle.isPressed()) {
            if (ativo) desligar("AutoMine DESLIGADO");
            else ligar();
        }
        if (!ativo) return;

        EntityPlayerSP p = mc.thePlayer;

        if (mc.currentScreen != null) {
            soltarTeclas();
            return;
        }
        if (p.getHealth() < VIDA_MINIMA) {
            desligar("Vida baixa, parei.");
            return;
        }

        // pulo automatico a cada 4 segundos (segura a tecla por 3 ticks)
        ticksPuloAuto++;
        if (ticksPuloAuto >= INTERVALO_PULO) ticksPuloAuto = 0;
        puloAuto = ticksPuloAuto < 3;

        if (multi > 1) processarPendentes();

        if (modoEscavar) tickEscavar(p);
        else if (modoTunel) tickTunel(p);
        else tickAndar(p);
    }

    // ------------------------------------------------------ modo andando

    private void tickAndar(EntityPlayerSP p) {
        if (p.isInLava()) {
            desligar("Lava! Parei.");
            return;
        }

        // 1) minerio ao alcance: para e quebra
        if (pausaTicks > 0) pausaTicks--;
        BlockPos minerio = (pausaTicks > 0) ? null : acharMinerio(p, RAIO_BUSCA, ALCANCE);
        if (minerio != null) {
            andar(false);
            pular(puloAuto);
            boolean alinhado = mirarSuave(p, minerio);
            escolherFerramenta(p, minerio);
            if (!alinhado) {
                esperaMira = ESPERA_MIRA[nivelVel - 1];
                atacar(false);
            } else if (esperaMira > 0) {
                esperaMira--;
                atacar(false);
            } else {
                atacar(true);
            }
            alvoMinerado = minerio;
            if (alinhado) multiQuebra(p, minerio);
            ticksParado = 0;
            return;
        }
        atacar(false);

        // acabou de quebrar um: vai ate onde ele estava pra pegar o drop
        if (alvoMinerado != null) {
            coletaPos = alvoMinerado;
            coletaTicks = 25;
            pausaTicks = PAUSA_BLOCO[nivelVel - 1];
            alvoMinerado = null;
            alvoLonge = null;
        }

        // 2) escolhe a direcao
        if (ticksVirando > 0) {
            ticksVirando--;
            andar(false);
            pular(puloAuto);
            suavizar(p);
            return;
        }

        if (coletaTicks > 0) {
            coletaTicks--;
            yawAlvo = yawPara(p, coletaPos);
        } else {
            ticksVarredura++;
            if (ticksVarredura >= 10) {
                ticksVarredura = 0;
                alvoLonge = acharMinerio(p, RAIO_LONGE, ALCANCE_LONGE);
            }
            double distOrigem = (origem == null) ? 0.0
                    : Math.hypot(p.posX - origem.getX(), p.posZ - origem.getZ());
            if (usarArea && !dentroXZ(p.posX, p.posZ)) {
                // fora da area: caminha ate ela
                yawAlvo = yawPara(p, centroArea());
            } else if (alvoLonge != null) {
                yawAlvo = yawPara(p, alvoLonge);
            } else if (!usarArea && distOrigem > raioPasseio) {
                // longe demais do ponto inicial: volta pra perto
                yawAlvo = yawPara(p, origem);
            } else {
                ticksAteTrocar--;
                if (ticksAteTrocar <= 0) virar(60, 120);
            }
        }

        // 3) olha o que tem a frente
        double rad = Math.toRadians(yawAlvo);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        BlockPos pe = new BlockPos(p.posX, p.posY, p.posZ);
        BlockPos frente = new BlockPos(p.posX + dx, p.posY, p.posZ + dz);

        boolean liquido = liquido(frente) || liquido(frente.down()) || liquido(frente.up());
        boolean buraco = mc.theWorld.isAirBlock(frente.down())
                && mc.theWorld.isAirBlock(frente.down(2))
                && mc.theWorld.isAirBlock(frente.down(3));
        boolean bloqueado = false;
        boolean degrau = false;
        // na borda da area: nao deixa dar o passo pra fora
        boolean saindo = usarArea && dentroXZ(p.posX, p.posZ)
                && !dentroXZ(frente.getX() + 0.5, frente.getZ() + 0.5);

        if (liquido || buraco || saindo) {
            bloqueado = true;
        } else if (!passavel(frente)) {
            if (passavel(frente.up()) && passavel(frente.up(2)) && passavel(pe.up(2))) degrau = true;
            else bloqueado = true;
        } else if (!passavel(frente.up())) {
            bloqueado = true;
        }

        if (bloqueado) {
            virar(90, 180);
            ticksVirando = ticksParaVirar(p);
            alvoLonge = null;
            ticksVarredura = -30;
            coletaTicks = 0;
            andar(false);
            pular(puloAuto);
            suavizar(p);
            return;
        }

        // 4) preso (nao saiu do lugar): vira e pula
        double mov = Math.hypot(p.posX - ultX, p.posZ - ultZ);
        ultX = p.posX;
        ultZ = p.posZ;
        if (mov < 0.01) ticksParado++;
        else ticksParado = 0;
        if (ticksParado > 30) {
            ticksParado = 0;
            virar(90, 180);
            ticksVirando = ticksParaVirar(p);
            alvoLonge = null;
            ticksVarredura = -30;
            coletaTicks = 0;
            andar(false);
            pular(true);
            suavizar(p);
            return;
        }

        // 5) anda
        float dif = MathHelper.wrapAngleTo180_float(yawAlvo - p.rotationYaw);
        suavizar(p);
        andar(Math.abs(dif) < 60.0f);
        pular(degrau || puloAuto);
    }

    private void virar(int minGraus, int maxGraus) {
        float graus = minGraus + rng.nextInt(maxGraus - minGraus + 1);
        yawAlvo += (rng.nextBoolean() ? 1.0f : -1.0f) * graus;
        sortearTrecho();
    }

    // quanto tempo anda numa direcao fixa antes de trocar (+-30% de variacao)
    private void sortearTrecho() {
        int base = trechoSeg * 20;
        ticksAteTrocar = base * 7 / 10 + rng.nextInt(base * 6 / 10 + 1);
    }

    private void suavizar(EntityPlayerSP p) {
        float passo = GIRO[nivelVel - 1];
        float dif = MathHelper.wrapAngleTo180_float(yawAlvo - p.rotationYaw);
        p.rotationYaw += Math.max(-passo, Math.min(passo, dif));
        float dp = 0.0f - p.rotationPitch;
        p.rotationPitch += Math.max(-passo, Math.min(passo, dp));
    }

    // quantos ticks leva pra girar ate yawAlvo na velocidade atual
    private int ticksParaVirar(EntityPlayerSP p) {
        float dif = Math.abs(MathHelper.wrapAngleTo180_float(yawAlvo - p.rotationYaw));
        int t = (int) Math.ceil(dif / GIRO[nivelVel - 1]);
        return Math.max(4, Math.min(60, t));
    }

    private float yawPara(EntityPlayerSP p, BlockPos pos) {
        double dx = pos.getX() + 0.5 - p.posX;
        double dz = pos.getZ() + 0.5 - p.posZ;
        return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
    }

    // ----------------------------------------------- modo escavar a area

    // bloco que pode ser quebrado no modo escavar: qualquer um, menos ar, liquido, inquebravel
    // (bedrock etc.) e blocos encostados em agua/lava (pra nao inundar a area)
    private boolean escavavel(BlockPos pos) {
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        if (b == Blocks.air) return false;
        if (b.getMaterial().isLiquid()) return false;
        if (b.getBlockHardness(mc.theWorld, pos) < 0.0f) return false;
        for (EnumFacing f : EnumFacing.values()) {
            if (liquido(pos.offset(f))) return false;
        }
        return true;
    }

    // so quebra o bloco debaixo dos pes se o buraco de baixo for seguro (sem liquido, sem queda grande)
    private boolean chaoSeguro(BlockPos pe) {
        if (liquido(pe.down(2)) || liquido(pe.down(3))) return false;
        return !(mc.theWorld.isAirBlock(pe.down(2))
                && mc.theWorld.isAirBlock(pe.down(3))
                && mc.theWorld.isAirBlock(pe.down(4)));
    }

    // proximo bloco da area ao alcance e visivel: de cima pra baixo, do mais perto pro mais longe
    private BlockPos acharBlocoEscavar(EntityPlayerSP p) {
        Vec3 olhos = new Vec3(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos base = new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos pe = new BlockPos(p.posX, p.posY, p.posZ);
        BlockPos melhor = null;
        double melhorScore = -Double.MAX_VALUE;

        for (int dx = -RAIO_BUSCA; dx <= RAIO_BUSCA; dx++) {
            for (int dy = -RAIO_BUSCA; dy <= RAIO_BUSCA; dy++) {
                for (int dz = -RAIO_BUSCA; dz <= RAIO_BUSCA; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    Vec3 centro = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double d = olhos.distanceTo(centro);
                    if (d > ALCANCE) continue;

                    double score = pos.getY() * 1000.0 - d;
                    if (score <= melhorScore) continue;
                    if (!dentroArea(pos)) continue;
                    if (!escavavel(pos)) continue;
                    if (pos.equals(pe.down()) && !chaoSeguro(pe)) continue;

                    MovingObjectPosition r = mc.theWorld.rayTraceBlocks(olhos, centro);
                    if (r != null && r.getBlockPos() != null && r.getBlockPos().equals(pos)) {
                        melhor = pos;
                        melhorScore = score;
                    }
                }
            }
        }
        return melhor;
    }

    // proximo bloco que ainda falta na area (mesmo fora do alcance), pra saber pra onde andar.
    // Areas muito grandes: so olha 20 blocos em volta de voce.
    private BlockPos acharProximoEscavar(EntityPlayerSP p) {
        int x0 = aMinX, x1 = aMaxX, y0 = aMinY, y1 = aMaxY, z0 = aMinZ, z1 = aMaxZ;
        long vol = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (vol > 60000L) {
            int px = (int) Math.floor(p.posX);
            int py = (int) Math.floor(p.posY);
            int pz = (int) Math.floor(p.posZ);
            x0 = Math.max(x0, px - 20);
            x1 = Math.min(x1, px + 20);
            y0 = Math.max(y0, py - 20);
            y1 = Math.min(y1, py + 20);
            z0 = Math.max(z0, pz - 20);
            z1 = Math.min(z1, pz + 20);
        }

        BlockPos melhor = null;
        double melhorScore = -Double.MAX_VALUE;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dh = Math.hypot(x + 0.5 - p.posX, z + 0.5 - p.posZ);
                for (int y = y0; y <= y1; y++) {
                    double score = y * 1000.0 - dh;
                    if (y > p.posY + 2) score -= 100000.0; // acima do alcance: por ultimo
                    if (score <= melhorScore) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!escavavel(pos)) continue;
                    melhor = pos;
                    melhorScore = score;
                }
            }
        }
        return melhor;
    }

    private void tickEscavar(EntityPlayerSP p) {
        if (!usarArea) {
            desligar("Defina a area antes com /automine pos1 e /automine pos2.");
            return;
        }
        if (p.isInLava()) {
            desligar("Lava! Parei.");
            return;
        }
        if (!dentroXZ(p.posX, p.posZ)) {
            desligar("Fique dentro da area pra escavar (de preferencia no ponto mais alto).");
            return;
        }

        pular(puloAuto);

        // acabou de quebrar o alvo: pequena pausa conforme a velocidade
        if (ultimoAlvoEscavar != null && mc.theWorld.isAirBlock(ultimoAlvoEscavar)) {
            pausaTicks = PAUSA_BLOCO[nivelVel - 1];
            ultimoAlvoEscavar = null;
        }
        if (pausaTicks > 0) {
            pausaTicks--;
            atacar(false);
            andar(false);
            return;
        }

        // 1) bloco ao alcance: quebra
        BlockPos alvo = acharBlocoEscavar(p);
        if (alvo != null) {
            andar(false);
            boolean alinhado = mirarSuave(p, alvo);
            escolherFerramenta(p, alvo);
            if (!alinhado) {
                esperaMira = ESPERA_MIRA[nivelVel - 1];
                atacar(false);
            } else if (esperaMira > 0) {
                esperaMira--;
                atacar(false);
            } else {
                atacar(true);
            }
            ultimoAlvoEscavar = alvo;
            ticksParado = 0;
            ultX = p.posX;
            ultZ = p.posZ;
            return;
        }
        atacar(false);

        // 2) nada ao alcance: acha o proximo que falta e anda ate la
        ticksVarredura++;
        if (alvoLonge == null || ticksVarredura >= 20 || mc.theWorld.isAirBlock(alvoLonge)) {
            ticksVarredura = 0;
            alvoLonge = acharProximoEscavar(p);
            if (alvoLonge == null) {
                modoEscavar = false; // volta ao modo normal pra proxima vez que ligar
                desligar("Area limpa, terminei. (Blocos encostados em agua/lava e inquebraveis foram pulados.)");
                return;
            }
        }

        double mov = Math.hypot(p.posX - ultX, p.posZ - ultZ);
        ultX = p.posX;
        ultZ = p.posZ;
        if (mov < 0.01) ticksParado++;
        else ticksParado = 0;
        if (ticksParado > 100) {
            desligar("Nao consigo alcancar o resto da area. Va pro ponto mais alto da area e ligue de novo.");
            return;
        }

        double dh = Math.hypot(alvoLonge.getX() + 0.5 - p.posX, alvoLonge.getZ() + 0.5 - p.posZ);
        yawAlvo = yawPara(p, alvoLonge);
        double rad = Math.toRadians(yawAlvo);
        BlockPos pe = new BlockPos(p.posX, p.posY, p.posZ);
        BlockPos frente = new BlockPos(p.posX - Math.sin(rad), p.posY, p.posZ + Math.cos(rad));

        boolean seguro = !liquido(frente) && !liquido(frente.down()) && !liquido(frente.up())
                && !(mc.theWorld.isAirBlock(frente.down())
                && mc.theWorld.isAirBlock(frente.down(2))
                && mc.theWorld.isAirBlock(frente.down(3)));
        boolean livre = seguro && passavel(frente) && passavel(frente.up());
        boolean degrau = seguro && !passavel(frente) && passavel(frente.up())
                && passavel(frente.up(2)) && passavel(pe.up(2));
        boolean dentro = dentroXZ(frente.getX() + 0.5, frente.getZ() + 0.5);

        float dif = MathHelper.wrapAngleTo180_float(yawAlvo - p.rotationYaw);
        suavizar(p);
        // perto demais na horizontal (alvo logo acima/abaixo): nao fica andando a toa
        andar(dh > 1.2 && (livre || degrau) && dentro && Math.abs(dif) < 60.0f);
        pular(degrau || puloAuto);
    }

    // ------------------------------------------------------- modo tunel

    private void tickTunel(EntityPlayerSP p) {
        pular(puloAuto);
        BlockPos pe = new BlockPos(p.posX, p.posY, p.posZ);
        BlockPos frentePe = pe.offset(direcao);
        BlockPos frenteCabeca = frentePe.up();
        BlockPos frente2 = frentePe.offset(direcao);

        // seguranca: liquido por perto
        BlockPos[] perigos = {
                frentePe, frenteCabeca, frentePe.down(),
                frente2, frente2.up(), frente2.down(), pe.down()
        };
        for (BlockPos b : perigos) {
            if (liquido(b)) {
                desligar("Liquido a frente, parei.");
                return;
            }
        }

        if (usarArea && dentroXZ(p.posX, p.posZ)
                && !dentroXZ(frentePe.getX() + 0.5, frentePe.getZ() + 0.5)) {
            desligar("Chegou no limite da area, parei.");
            return;
        }

        // 1) minerio visivel ao alcance
        BlockPos minerio = acharMinerio(p, RAIO_BUSCA, ALCANCE);
        if (minerio != null) {
            boolean alinhado = mirarSuave(p, minerio);
            escolherFerramenta(p, minerio);
            atacar(alinhado);
            if (alinhado) multiQuebra(p, minerio);
            andar(false);
            return;
        }

        // 2) tunel: quebra o que estiver na frente (cabeca primeiro, depois os pes)
        BlockPos quebrar = null;
        if (!passavel(frenteCabeca)) quebrar = frenteCabeca;
        else if (!passavel(frentePe)) quebrar = frentePe;

        if (quebrar != null) {
            if (dureza(quebrar) < 0) {
                desligar("Bloco indestrutivel a frente, parei.");
                return;
            }
            boolean ok = mirarSuave(p, quebrar);
            escolherFerramenta(p, quebrar);
            atacar(ok);
            andar(false);
        } else {
            // caminho livre: confere se nao tem buraco antes de andar
            if (mc.theWorld.isAirBlock(frentePe.down()) && mc.theWorld.isAirBlock(frentePe.down().down())) {
                desligar("Buraco a frente, parei.");
                return;
            }
            yawAlvo = direcao.getHorizontalIndex() * 90.0f;
            float difTunel = MathHelper.wrapAngleTo180_float(yawAlvo - p.rotationYaw);
            suavizar(p);
            atacar(false);
            andar(Math.abs(difTunel) < 15.0f);
        }
    }

    // ---------------------------------------------------- dropar inventario

    // coloca na fila todos os slots com item: mochila (9-35) e, se pedido, hotbar (36-44).
    // A armadura (5-8) nao e dropada.
    private void droparInventario(boolean incluirHotbar) {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.playerController == null) return;
        filaDrop.clear();
        int ultimo = incluirHotbar ? 44 : 35;
        for (int slot = 9; slot <= ultimo; slot++) {
            if (p.inventoryContainer.getSlot(slot).getHasStack()) filaDrop.add(slot);
        }
        if (filaDrop.isEmpty()) {
            msg("Nada pra dropar.");
        } else {
            msg("Dropando " + filaDrop.size() + " pilhas...");
        }
    }

    // dropa alguns slots por tick (pra nao mandar tudo de uma vez pro servidor)
    private void processarFilaDrop() {
        if (filaDrop.isEmpty()) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.playerController == null) {
            filaDrop.clear();
            return;
        }
        for (int i = 0; i < DROP_POR_TICK && !filaDrop.isEmpty(); i++) {
            int slot = filaDrop.poll();
            if (p.inventoryContainer.getSlot(slot).getHasStack()) {
                // modo 4 (soltar), botao 1 = pilha inteira
                mc.playerController.windowClick(p.inventoryContainer.windowId, slot, 1, 4, p);
            }
        }
        if (filaDrop.isEmpty()) msg("Inventario dropado.");
    }

    // botao "Dropar tudo" na tela do inventario
    @SubscribeEvent
    public void aoAbrirGui(GuiScreenEvent.InitGuiEvent.Post ev) {
        if (ev.gui instanceof GuiIngameMenu) {
            // menu de pausa (Esc): botao grande no canto de cima
            ev.buttonList.add(new GuiButton(BTN_TOGGLE, 4, 4, 130, 20, textoToggle()));
            ev.buttonList.add(new GuiButton(BTN_PLAYERS, 4, 28, 130, 20, textoPlayers()));
            return;
        }
        if (ev.gui instanceof GuiInventory) {
            ev.buttonList.add(new GuiButton(BTN_DROPAR, ev.gui.width / 2 + 92, ev.gui.height / 2 - 83, 72, 20, "Dropar tudo"));
            ev.buttonList.add(new GuiButton(BTN_VENDER, ev.gui.width / 2 + 92, ev.gui.height / 2 - 59, 72, 20, "Vender"));
            ev.buttonList.add(new GuiButton(BTN_TOGGLE, ev.gui.width / 2 + 92, ev.gui.height / 2 - 35, 72, 20, ativo ? "Desligar" : "Ligar mod"));
        }
    }

    @SubscribeEvent
    public void aoClicarBotao(GuiScreenEvent.ActionPerformedEvent.Post ev) {
        if (ev.button.id == BTN_PLAYERS && ev.gui instanceof GuiIngameMenu) {
            // liga/desliga e deixa o menu aberto, so troca o texto do botao
            alternarPlayers();
            ev.button.displayString = textoPlayers();
            return;
        }
        if (ev.button.id == BTN_TOGGLE && (ev.gui instanceof GuiInventory || ev.gui instanceof GuiIngameMenu)) {
            alternar();
            return;
        }
        if (!(ev.gui instanceof GuiInventory)) return;
        if (ev.button.id == BTN_DROPAR) {
            droparInventario(true);
        } else if (ev.button.id == BTN_VENDER) {
            vender();
        }
    }

    // liga/desliga o mod e volta pro jogo (o mod fica parado enquanto uma tela esta aberta)
    private void alternar() {
        if (ativo) desligar("AutoMine DESLIGADO");
        else ligar();
        mc.displayGuiScreen(null);
    }

    private String textoToggle() {
        return ativo ? "Desligar AutoMine" : "Ligar AutoMine";
    }

    // aviso verde no canto da tela enquanto o mod esta ligado
    @SubscribeEvent
    public void aoDesenharHud(RenderGameOverlayEvent.Post ev) {
        if (ev.type != RenderGameOverlayEvent.ElementType.ALL) return;
        int y = 4;
        if (ativo) {
            String t = "AutoMine: LIGADO" + (usarArea ? " | area" : "") + (multi > 1 ? " | multi " + multi : "")
                    + (modoEscavar ? " | escavando" : "");
            mc.fontRendererObj.drawStringWithShadow(t, 4.0f, (float) y, 0x55FF55);
            y += 12;
        }
        desenharLista(y);
    }

    // ------------------------------------------------------- ver players

    private void alternarPlayers() {
        boolean ligar = !(listaPlayers || linhasPlayers);
        listaPlayers = ligar;
        linhasPlayers = ligar;
    }

    private String textoPlayers() {
        return (listaPlayers || linhasPlayers) ? "Ver players: LIGADO" : "Ver players: desligado";
    }

    // lista no canto da tela: nome e distancia, do mais perto pro mais longe
    private void desenharLista(int y) {
        if (!listaPlayers || mc.theWorld == null || mc.thePlayer == null) return;
        final EntityPlayerSP eu = mc.thePlayer;

        List<EntityPlayer> perto = new ArrayList<EntityPlayer>();
        for (EntityPlayer pl : mc.theWorld.playerEntities) {
            if (pl == eu) continue;
            if (eu.getDistanceToEntity(pl) <= alcancePlayers) perto.add(pl);
        }
        Collections.sort(perto, new Comparator<EntityPlayer>() {
            @Override
            public int compare(EntityPlayer a, EntityPlayer b) {
                return Double.compare(eu.getDistanceToEntity(a), eu.getDistanceToEntity(b));
            }
        });

        if (perto.isEmpty()) {
            mc.fontRendererObj.drawStringWithShadow("Players perto: nenhum", 4.0f, (float) y, 0xAAAAAA);
            return;
        }
        mc.fontRendererObj.drawStringWithShadow("Players perto (" + perto.size() + "):", 4.0f, (float) y, 0xFFFF55);
        y += 11;
        for (int i = 0; i < perto.size() && i < 8; i++) {
            EntityPlayer pl = perto.get(i);
            int d = (int) eu.getDistanceToEntity(pl);
            int cor = d < 15 ? 0xFF5555 : (d < 35 ? 0xFFAA00 : 0xFFFFFF);
            mc.fontRendererObj.drawStringWithShadow(pl.getName() + "  " + d + "m", 4.0f, (float) y, cor);
            y += 10;
        }
    }

    // linha da mira ate cada player (vermelha = perto, verde = longe), vista atraves das paredes
    @SubscribeEvent
    public void aoRenderMundo(RenderWorldLastEvent ev) {
        if (!linhasPlayers || mc.theWorld == null || mc.thePlayer == null) return;
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) return;
        float pt = ev.partialTicks;

        double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        Vec3 look = viewer.getLook(pt);
        double sx = vx + look.xCoord;
        double sy = vy + viewer.getEyeHeight() + look.yCoord;
        double sz = vz + look.zCoord;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-vx, -vy, -vz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(2.0f);

        for (EntityPlayer pl : mc.theWorld.playerEntities) {
            if (pl == mc.thePlayer || pl.isDead) continue;
            double dist = mc.thePlayer.getDistanceToEntity(pl);
            if (dist > alcancePlayers) continue;

            double px = pl.lastTickPosX + (pl.posX - pl.lastTickPosX) * pt;
            double py = pl.lastTickPosY + (pl.posY - pl.lastTickPosY) * pt + pl.height / 2.0;
            double pz = pl.lastTickPosZ + (pl.posZ - pl.lastTickPosZ) * pt;

            float f = (float) Math.min(1.0, dist / alcancePlayers);
            GlStateManager.color(1.0f - f, f, 0.0f, 0.9f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(sx, sy, sz);
            GL11.glVertex3d(px, py, pz);
            GL11.glEnd();
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // manda /vender pro servidor (ou so abre o chat com /vender digitado, conforme o modo)
    private void vender() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        if (venderEnviar) {
            p.sendChatMessage("/vender");
        } else {
            mc.displayGuiScreen(new GuiChat("/vender"));
        }
    }

    // ------------------------------------------------------ multi-quebra

    private void enviarDig(C07PacketPlayerDigging.Action acao, BlockPos pos, EnumFacing face) {
        if (mc.getNetHandler() != null) {
            mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(acao, pos, face));
        }
    }

    // manda START_DESTROY_BLOCK de ate (multi - 1) blocos extras visiveis ao alcance;
    // depois do tempo de quebra manda o STOP (ver processarPendentes)
    private void multiQuebra(EntityPlayerSP p, BlockPos principal) {
        if (multi <= 1) return;
        int faltam = (multi - 1) - pendentes.size();
        if (faltam <= 0) return;

        Vec3 olhos = new Vec3(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos base = new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        List<Alvo> achados = new ArrayList<Alvo>();

        for (int dx = -RAIO_BUSCA; dx <= RAIO_BUSCA; dx++) {
            for (int dy = -RAIO_BUSCA; dy <= RAIO_BUSCA; dy++) {
                for (int dz = -RAIO_BUSCA; dz <= RAIO_BUSCA; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (pos.equals(principal)) continue;
                    Block b = mc.theWorld.getBlockState(pos).getBlock();
                    if (!minerios.contains(b)) continue;
                    if (usarArea && !dentroArea(pos)) continue;

                    boolean jaPendente = false;
                    for (Pendente pd : pendentes) {
                        if (pd.pos.equals(pos)) {
                            jaPendente = true;
                            break;
                        }
                    }
                    if (jaPendente) continue;
                    Integer t = tentativas.get(pos);
                    if (t != null && t >= 2) continue;

                    Vec3 centro = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double d = olhos.distanceTo(centro);
                    if (d > ALCANCE) continue;

                    MovingObjectPosition r = mc.theWorld.rayTraceBlocks(olhos, centro);
                    if (r != null && r.getBlockPos() != null && r.getBlockPos().equals(pos)) {
                        achados.add(new Alvo(pos, r.sideHit, d));
                    }
                }
            }
        }

        Collections.sort(achados, new Comparator<Alvo>() {
            @Override
            public int compare(Alvo a, Alvo b) {
                return Double.compare(a.dist, b.dist);
            }
        });

        for (int i = 0; i < achados.size() && i < faltam && i < ENVIOS_POR_TICK; i++) {
            Alvo a = achados.get(i);
            Block b = mc.theWorld.getBlockState(a.pos).getBlock();
            float rel = b.getPlayerRelativeBlockHardness(p, mc.theWorld, a.pos);
            int ticks = (rel <= 0.0f) ? 100 : (int) Math.ceil(0.7f / rel) + 1;
            ticks = Math.max(1, Math.min(200, ticks));

            enviarDig(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, a.pos, a.face);
            pendentes.add(new Pendente(a.pos, a.face, ticks));
            Integer t = tentativas.get(a.pos);
            tentativas.put(a.pos, t == null ? 1 : t + 1);
        }
    }

    private void processarPendentes() {
        for (int i = pendentes.size() - 1; i >= 0; i--) {
            Pendente pd = pendentes.get(i);
            Block b = mc.theWorld.getBlockState(pd.pos).getBlock();
            if (!minerios.contains(b)) {
                // ja quebrou (ou sumiu)
                pendentes.remove(i);
                tentativas.remove(pd.pos);
                continue;
            }
            pd.ticks--;
            if (pd.ticks <= 0) {
                enviarDig(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pd.pos, pd.face);
                pendentes.remove(i);
            }
        }
    }

    private void cancelarPendentes() {
        for (Pendente pd : pendentes) {
            enviarDig(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, pd.pos, pd.face);
        }
        pendentes.clear();
        tentativas.clear();
    }

    // --------------------------------------------------------------- helpers

    private boolean passavel(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock().isPassable(mc.theWorld, pos);
    }

    private boolean liquido(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock().getMaterial().isLiquid();
    }

    private float dureza(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock().getBlockHardness(mc.theWorld, pos);
    }

    private BlockPos acharMinerio(EntityPlayerSP p, int raio, double alcance) {
        Vec3 olhos = new Vec3(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos base = new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos melhor = null;
        double melhorDist = Double.MAX_VALUE;

        for (int dx = -raio; dx <= raio; dx++) {
            for (int dy = -raio; dy <= raio; dy++) {
                for (int dz = -raio; dz <= raio; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    Block b = mc.theWorld.getBlockState(pos).getBlock();
                    if (!minerios.contains(b)) continue;
                    if (usarArea && !dentroArea(pos)) continue;

                    Vec3 centro = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double d = olhos.distanceTo(centro);
                    if (d > alcance || d >= melhorDist) continue;

                    // so se estiver visivel (nada no meio)
                    MovingObjectPosition r = mc.theWorld.rayTraceBlocks(olhos, centro);
                    if (r != null && r.getBlockPos() != null && r.getBlockPos().equals(pos)) {
                        melhor = pos;
                        melhorDist = d;
                    }
                }
            }
        }
        return melhor;
    }

    // gira a camera aos poucos ate o bloco; devolve true quando ja esta olhando pra ele
    private boolean mirarSuave(EntityPlayerSP p, BlockPos pos) {
        double dx = pos.getX() + 0.5 - p.posX;
        double dy = pos.getY() + 0.5 - (p.posY + p.getEyeHeight());
        double dz = pos.getZ() + 0.5 - p.posZ;
        double distH = Math.sqrt(dx * dx + dz * dz);
        float yawT = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitchT = (float) -(Math.atan2(dy, distH) * 180.0 / Math.PI);

        float passo = GIRO[nivelVel - 1];
        float dYaw = MathHelper.wrapAngleTo180_float(yawT - p.rotationYaw);
        float dPitch = pitchT - p.rotationPitch;
        p.rotationYaw += Math.max(-passo, Math.min(passo, dYaw));
        p.rotationPitch += Math.max(-passo, Math.min(passo, dPitch));
        p.rotationPitch = MathHelper.clamp_float(p.rotationPitch, -90.0f, 90.0f);

        float restYaw = Math.abs(MathHelper.wrapAngleTo180_float(yawT - p.rotationYaw));
        float restPitch = Math.abs(pitchT - p.rotationPitch);
        return restYaw < 4.0f && restPitch < 4.0f;
    }

    private void escolherFerramenta(EntityPlayerSP p, BlockPos pos) {
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        int melhor = p.inventory.currentItem;
        float forca = 0.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            float f = (s == null) ? 1.0f : s.getStrVsBlock(b);
            if (f > forca) {
                forca = f;
                melhor = i;
            }
        }
        p.inventory.currentItem = melhor;
    }

    // --------------------------------------------------------------- comando

    private class CommandAutoMine extends CommandBase {
        @Override
        public String getCommandName() {
            return "automine";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/automine [tudo|diamante|ferro|ouro|carvao|redstone|lapis|blocolapis|blocos|esmeralda|quartzo|parar] ou /automine tunel [minerio] | escavar | pos1 | pos2 | area on/off/limpar | velocidade 1-6 | multi <numero> | incluirblocos on/off | players on/off/lista/linhas/alcance | dropar [mochila] | vender | vendermodo enviar/abrir";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                if (ativo) desligar("AutoMine DESLIGADO");
                else ligar();
                return;
            }
            String a = args[0].toLowerCase();
            if (a.equals("parar") || a.equals("off")) {
                desligar("AutoMine DESLIGADO");
            } else if (a.equals("pos1") || a.equals("pos2")) {
                EntityPlayerSP pl = mc.thePlayer;
                BlockPos aqui = new BlockPos(pl.posX, pl.posY, pl.posZ);
                if (a.equals("pos1")) pos1 = aqui;
                else pos2 = aqui;
                msg(a + " = " + aqui.getX() + " " + aqui.getY() + " " + aqui.getZ());
                if (pos1 != null && pos2 != null) {
                    atualizarArea();
                    usarArea = true;
                    msg("Area ligada: " + descricaoArea());
                } else {
                    msg("Agora va ate o outro canto e use /automine " + (a.equals("pos1") ? "pos2" : "pos1"));
                }
            } else if (a.equals("area")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("limpar")) {
                    pos1 = null;
                    pos2 = null;
                    usarArea = false;
                    msg("Area removida.");
                } else if (o.equals("off") || o.equals("desligar")) {
                    usarArea = false;
                    msg("Area desligada (volta a andar livre).");
                } else if (o.equals("on") || o.equals("ligar")) {
                    if (pos1 != null && pos2 != null) {
                        atualizarArea();
                        usarArea = true;
                        msg("Area ligada: " + descricaoArea());
                    } else {
                        msg("Defina antes com /automine pos1 e /automine pos2.");
                    }
                } else {
                    msg("Area: " + descricaoArea());
                    msg("Uso: /automine area on | off | limpar");
                }
            } else if (a.equals("escavar")) {
                if (pos1 == null || pos2 == null) {
                    msg("Defina a area antes: /automine pos1 e /automine pos2.");
                } else {
                    atualizarArea();
                    usarArea = true;
                    modoEscavar = true;
                    modoTunel = false;
                    ligar();
                }
            } else if (a.equals("players")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("lista")) {
                    listaPlayers = !listaPlayers;
                    msg("Lista de players: " + (listaPlayers ? "LIGADA" : "desligada"));
                } else if (o.equals("linhas")) {
                    linhasPlayers = !linhasPlayers;
                    msg("Linhas ate os players: " + (linhasPlayers ? "LIGADAS" : "desligadas"));
                } else if (o.equals("on") || o.equals("tudo")) {
                    listaPlayers = true;
                    linhasPlayers = true;
                    msg("Ver players: lista e linhas LIGADAS (alcance " + alcancePlayers + ").");
                } else if (o.equals("off")) {
                    listaPlayers = false;
                    linhasPlayers = false;
                    msg("Ver players desligado.");
                } else if (o.equals("alcance")) {
                    int n = -1;
                    if (args.length > 2) {
                        try {
                            n = Integer.parseInt(args[2]);
                        } catch (NumberFormatException ex) {
                            n = -1;
                        }
                    }
                    if (n >= 10 && n <= 300) {
                        alcancePlayers = n;
                        msg("Alcance pra ver players: " + n + " blocos.");
                    } else {
                        msg("Uso: /automine players alcance <10-300>");
                    }
                } else {
                    msg("Players: lista " + (listaPlayers ? "LIGADA" : "desligada")
                            + ", linhas " + (linhasPlayers ? "LIGADAS" : "desligadas")
                            + ", alcance " + alcancePlayers);
                    msg("Uso: /automine players on | off | lista | linhas | alcance <n>");
                }
            } else if (a.equals("incluirblocos")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("on")) {
                    incluirBlocos = true;
                    definirFiltro(filtroAtual);
                    msg("Agora tambem quebra os blocos compactos (diamante, ferro, ouro, redstone, lapis, esmeralda).");
                } else if (o.equals("off")) {
                    incluirBlocos = false;
                    definirFiltro(filtroAtual);
                    msg("Nao quebra mais os blocos compactos, so minerios.");
                } else {
                    msg("Blocos compactos: " + (incluirBlocos ? "LIGADO" : "desligado") + ". Uso: /automine incluirblocos on | off");
                }
            } else if (a.equals("vender")) {
                vender();
            } else if (a.equals("vendermodo")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("enviar")) {
                    venderEnviar = true;
                    msg("Vender: envia /vender direto.");
                } else if (o.equals("abrir")) {
                    venderEnviar = false;
                    msg("Vender: abre o chat com /vender digitado (voce aperta Enter).");
                } else {
                    msg("Uso: /automine vendermodo enviar | abrir");
                }
            } else if (a.equals("dropar")) {
                // /automine dropar          -> tudo (mochila + hotbar)
                // /automine dropar mochila  -> so a mochila, mantem a hotbar
                boolean soMochila = args.length > 1 && args[1].equalsIgnoreCase("mochila");
                droparInventario(!soMochila);
            } else if (a.equals("multi")) {
                int n = -1;
                if (args.length > 1) {
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        n = -1;
                    }
                }
                if (n >= 1 && n <= MULTI_MAX) {
                    multi = n;
                    if (n == 1) {
                        cancelarPendentes();
                        msg("Multi-quebra desligada (1 bloco por vez).");
                    } else {
                        msg("Multi-quebra: " + n + " blocos de uma vez. Em survival o servidor pode ignorar os extras.");
                    }
                } else {
                    msg("Uso: /automine multi <1-" + MULTI_MAX + ">  (1 = desligado)");
                }
            } else if (a.equals("trecho") || a.equals("raio") || a.equals("velocidade")) {
                int n = -1;
                if (args.length > 1) {
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        n = -1;
                    }
                }
                if (a.equals("trecho") && n >= 5 && n <= 300) {
                    trechoSeg = n;
                    sortearTrecho();
                    msg("Troca de direcao a cada ~" + n + " segundos");
                } else if (a.equals("raio") && n >= 10 && n <= 500) {
                    raioPasseio = n;
                    msg("Raio maximo: " + n + " blocos");
                } else if (a.equals("velocidade") && n >= 1 && n <= 6) {
                    nivelVel = n;
                    msg("Velocidade: " + n + " (1 = bem lento, 6 = maximo)");
                } else {
                    msg("Uso: /automine trecho <5-300> | raio <10-500> | velocidade <1-6>");
                }
            } else if (a.equals("tunel")) {
                String f = args.length > 1 ? args[1].toLowerCase() : "tudo";
                if (definirFiltro(f)) {
                    modoTunel = true;
                    modoEscavar = false;
                    ligar();
                    msg("Minerando: " + f);
                } else {
                    msg("Uso: " + getCommandUsage(sender));
                }
            } else if (definirFiltro(a)) {
                modoTunel = false;
                modoEscavar = false;
                ligar();
                msg("Minerando: " + a);
            } else {
                msg("Uso: " + getCommandUsage(sender));
            }
        }
    }
}
