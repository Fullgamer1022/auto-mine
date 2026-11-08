package com.exemplo.automine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
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
import net.minecraft.init.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
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
 * AJUDA NO JOGO: digite  /automine ajuda   (e  /automine status  pra ver os ajustes atuais).
 *
 * LIGAR / DESLIGAR
 *   /automine (liga/desliga) | /automine parar | tecla M | botao no menu Esc | botao no inventario
 *
 * O QUE MINERAR
 *   /automine tudo|diamante|ferro|ouro|carvao|redstone|lapis|esmeralda|quartzo
 *   /automine incluirblocos on|off  -> tambem quebra blocos compactos (padrao: off)
 *   /automine blocos | blocolapis   -> so blocos compactos
 *   /automine tunel [minerio]       -> cava em linha reta
 *
 * AREA DEMARCADA
 *   GRAVETO na mao + botao direito num bloco -> marca pos1, depois pos2 (caixa verde/vermelha/amarela)
 *   /automine pos1 | pos2           -> marca os cantos onde voce esta (a area liga sozinha; a altura Y conta)
 *   /automine area on|off|limpar    -> liga, desliga ou apaga a area
 *   /automine escavar               -> limpa a area camada por camada: varre a de cima inteira
 *                                      andando e so depois desce pra proxima
 *
 * AJUSTES
 *   /automine velocidade 1-6        -> 1 = lento ... 6 = maximo (padrao 6)
 *   /automine limite N              -> para depois de quebrar N blocos (0 = sem limite)
 *   /automine nuker [on|off|N]      -> nuker: varios blocos por tick (funciona com o AutoMine desligado tambem)
 *   /automine nuker minerios|tudo | forcar on|off
 *   /automine reach N               -> alcance de quebra, 3 a 8 (padrao 4); tambem vale pro seu clique
 *   /automine reach mouse on|off    -> liga/desliga o reach no seu proprio clique
 *   /automine doidao on|off         -> quebra qualquer bloco ao alcance (padrao off)
 *   /automine camadas on|off        -> escavar camada por camada (padrao on)
 *   /automine turbo on|max|off      -> mineracao acelerada (padrao on)
 *   /automine multi N               -> quantos blocos de uma vez (1 a 500)
 *   /automine multi on|off          -> LIGA/DESLIGA o multi (tecla N ou botao). Comeca desligado.
 *   /automine trecho S | raio N     -> segundos por direcao | distancia maxima do ponto inicial
 *
 * MACRO (grava o que voce faz e repete em loop)
 *   /automine macro gravar | parar | tocar [voltas] | salvar <nome> | carregar <nome> | lista
 *   Teclas: G = gravar/parar gravacao, H = tocar/parar. Botoes no menu Esc.
 *
 * EXTRAS
 *   Ver players: tecla P, botao no Esc, /automine players on|off|lista|linhas|alcance N
 *   Dropar inventario: tecla K, botao no inventario, /automine dropar [mochila]
 *   Vender: tecla L, botao no inventario, /automine vender | vendermodo enviar|abrir
 *
 * NOTAS
 *   - Modo padrao (andando): anda em direcoes aleatorias, desvia de buracos/liquidos/paredes,
 *     vai ate o minerio, quebra e pega o drop. Nao quebra pedra comum.
 *   - Multi: manda varios pedidos de quebra juntos. So funciona de verdade em criativo ou em
 *     servidor que nao valida; em survival costuma ser ignorado e pode dar kick/bloco fantasma.
 *   - Para sozinho se cair em lava, se a vida ficar baixa ou ao atingir o limite de blocos.
 */
@Mod(modid = AutoMine.MODID, name = "AutoMine", version = "1.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class AutoMine {
    public static final String MODID = "automine";

    private static final double ALCANCE = 4.0;
    private static final int RAIO_BUSCA = 4;
    private static final double ALCANCE_LONGE = 10.0;
    private boolean reachMouse = true;  // o reach tambem vale pro SEU clique/toque (alem do automatico)
    private double reach = 4.0;           // alcance de quebra (4 a 6); acima de 4.4 usa o controlador direto

    // estende a mira do jogo: o que VOCE clica/toca tambem alcanca o reach definido
    @SubscribeEvent
    public void aoRenderTick(TickEvent.RenderTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !reachMouse || reach <= 4.5) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;
        MovingObjectPosition atual = mc.objectMouseOver;
        if (atual != null && atual.typeOfHit != MovingObjectPosition.MovingObjectType.MISS) return;
        EntityPlayerSP pl = mc.thePlayer;
        Vec3 olhos = pl.getPositionEyes(1.0f);
        Vec3 olhar = pl.getLook(1.0f);
        Vec3 fim = olhos.addVector(olhar.xCoord * reach, olhar.yCoord * reach, olhar.zCoord * reach);
        MovingObjectPosition r = mc.theWorld.rayTraceBlocks(olhos, fim, false, false, true);
        if (r != null && r.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            mc.objectMouseOver = r;
        }
    }

    private int raioBusca() {
        return (int) Math.ceil(reach);
    }
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
    private boolean turbo = true;          // mineracao acelerada (sem atraso entre blocos, quebra aos 70%)
    private boolean turboFalhou = false;
    private boolean turboMax = false;      // alem do atraso, pula os 30% finais (pode fazer o servidor devolver o bloco)
    private BlockPos ultimoAlvoEscavar = null;
    // nuker: quebra varios blocos por tick, direto por pacotes, seguindo a regra do servidor
    private boolean nuker = false;
    private int nukerQtd = 20;             // maximo de blocos "instantaneos" por tick
    private boolean nukerForcar = false;   // manda START+STOP em tudo (so serve em servidor que nao valida o tempo)
    private boolean nukerSoMinerio = false;
    private int nukerQuebrados = 0;
    private int tickGlobal = 0;
    private BlockPos nukerLento = null;
    private int nukerTicks = 0;
    private int nukerSlot = -1;
    private final Map<BlockPos, Integer> nukerEnviado = new HashMap<BlockPos, Integer>();
    private final Map<BlockPos, Integer> nukerFalhas = new HashMap<BlockPos, Integer>();

    private boolean doidao = false;      // quebra QUALQUER bloco ao alcance de um player normal
    private boolean camadas = true;     // escavar camada por camada (off = quebra o que estiver mais perto)
    private int camadaEscavar = Integer.MIN_VALUE; // altura (Y) da camada que esta sendo limpa

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
    private static final int BTN_MULTI = 9005;
    private static final int BTN_GRAVAR = 9006;
    private static final int BTN_TOCAR = 9007;

    // ver players por perto: lista na tela + linhas ate cada um (aparecem atraves das paredes)
    private boolean listaPlayers = false;
    private boolean linhasPlayers = false;
    private int alcancePlayers = 64;
    private KeyBinding teclaPlayers;
    private KeyBinding teclaMulti;
    private KeyBinding teclaGravar;
    private KeyBinding teclaTocar;
    private KeyBinding teclaVender;
    private boolean venderEnviar = true; // true = envia /vender direto; false = so abre o chat com o texto
    private KeyBinding teclaDropar;
    private final ArrayDeque<Integer> filaDrop = new ArrayDeque<Integer>();

    // multi-quebra: manda pedidos de quebra de ate (multi - 1) blocos extras junto com o principal
    private static final int MULTI_MAX = 500;      // teto so pra evitar digitar errado
    private static final int ENVIOS_POR_TICK = 5;  // quantos pedidos novos manda por tick (evita flood no servidor)
    private int multi = 5;                // quantos blocos de uma vez (1 a MULTI_MAX)
    private boolean multiLigado = false;  // o multi so age quando VOCE liga (comando, tecla N ou botao)

    // limite de blocos quebrados por rodada (0 = sem limite)
    private int limiteBlocos = 0;
    private int quebrados = 0;
    private BlockPos alvoContagem = null;

    // multi com voce quebrando: true = os extras sao do MESMO tipo do bloco que voce quebra;
    // false = qualquer bloco por perto
    private boolean multiSoIgual = true;
    private final List<Pendente> pendentes = new ArrayList<Pendente>();
    private final Map<BlockPos, Integer> tentativas = new HashMap<BlockPos, Integer>();

    private static class Pendente {
        final BlockPos pos;
        final EnumFacing face;
        final Block tipo; // tipo do bloco quando o pedido foi feito (se mudar, ele quebrou)
        int ticks;

        Pendente(BlockPos pos, EnumFacing face, int ticks, Block tipo) {
            this.pos = pos;
            this.face = face;
            this.ticks = ticks;
            this.tipo = tipo;
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
        teclaMulti = new KeyBinding("Ligar/desligar multi-quebra", Keyboard.KEY_N, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaMulti);
        teclaGravar = new KeyBinding("Macro: gravar / parar gravacao", Keyboard.KEY_G, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaGravar);
        teclaTocar = new KeyBinding("Macro: tocar / parar", Keyboard.KEY_H, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaTocar);
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
        if (gravando) pararGravacao();
        if (reproduzindo) pararMacro("Macro parada porque o mod foi ligado.");
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
        quebrados = 0;
        alvoContagem = null;
        camadaEscavar = Integer.MIN_VALUE;
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
        if (nuker) on = false; // com o nuker ligado quem quebra e ele
        // reach maior que o do jogo: quebra pelo controlador, sem depender da mira do jogo
        if (on && reach > 4.4 && alvoContagem != null && mc.thePlayer != null && mc.theWorld != null
                && mc.playerController != null && !mc.theWorld.isAirBlock(alvoContagem)) {
            EntityPlayerSP pl = mc.thePlayer;
            Vec3 olhos = new Vec3(pl.posX, pl.posY + pl.getEyeHeight(), pl.posZ);
            Vec3 c = new Vec3(alvoContagem.getX() + 0.5, alvoContagem.getY() + 0.5, alvoContagem.getZ() + 0.5);
            if (olhos.distanceTo(c) > 4.3) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                MovingObjectPosition r = mc.theWorld.rayTraceBlocks(olhos, c);
                EnumFacing f = (r != null && r.sideHit != null) ? r.sideHit : EnumFacing.UP;
                mc.playerController.onPlayerDamageBlock(alvoContagem, f);
                pl.swingItem();
                return;
            }
        }
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
            gravando = false;
            reproduzindo = false;
            pendentes.clear();
            tentativas.clear();
            nukerEnviado.clear();
            nukerLento = null;
            return;
        }

        if (teclaDropar.isPressed()) droparInventario(true);
        if (teclaVender.isPressed()) vender();
        if (teclaMulti.isPressed()) alternarMulti(!multiLigado);
        if (teclaGravar.isPressed()) {
            if (gravando) pararGravacao();
            else iniciarGravacao();
        }
        if (teclaTocar.isPressed()) {
            if (reproduzindo) pararMacro("Macro parada.");
            else iniciarMacro(0);
        }
        if (teclaPlayers.isPressed()) {
            alternarPlayers();
            msg("Ver players: " + (listaPlayers ? "LIGADO" : "desligado"));
        }
        processarFilaDrop();

        if (teclaToggle.isPressed()) {
            if (ativo) desligar("AutoMine DESLIGADO");
            else ligar();
        }
        if (turbo && !turboFalhou) aplicarTurbo();
        tickGlobal++;
        if (nuker) tickNuker(mc.thePlayer);
        // macro: grava cada tick do que voce faz / repete o que foi gravado
        if (gravando) gravarFrame(mc.thePlayer);
        if (reproduzindo) {
            reproduzirFrame(mc.thePlayer);
            return;
        }

        if (!ativo) {
            // mod desligado: o multi ajuda enquanto VOCE quebra os blocos
            if (multiAtivo() && mc.currentScreen == null) {
                processarPendentes();
                multiManual(mc.thePlayer);
            }
            return;
        }

        EntityPlayerSP p = mc.thePlayer;

        if (mc.currentScreen != null) {
            soltarTeclas();
            return;
        }
        if (p.getHealth() < VIDA_MINIMA) {
            desligar("Vida baixa, parei.");
            return;
        }

        // pulo automatico removido (o pulo so acontece pra subir degrau)
        puloAuto = false;

        if (multiAtivo()) processarPendentes();

        // conta os blocos quebrados e para no limite
        contarQuebras();
        if (limiteBlocos > 0 && quebrados >= limiteBlocos) {
            desligar("Limite atingido: " + quebrados + " blocos quebrados. Parei.");
            return;
        }

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
        BlockPos minerio = (pausaTicks > 0) ? null : acharMinerio(p, raioBusca(), reach);
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
            alvoContagem = minerio;
            if (alinhado) multiQuebra(p, minerio, false);
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

        int rb = raioBusca();
        for (int dx = -rb; dx <= rb; dx++) {
            for (int dy = -rb; dy <= rb; dy++) {
                for (int dz = -rb; dz <= rb; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    Vec3 centro = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double d = olhos.distanceTo(centro);
                    if (d > reach) continue;

                    // so a camada de trabalho (a de cima limpa inteira antes de descer)
                    if (camadaEscavar != Integer.MIN_VALUE && pos.getY() != camadaEscavar) continue;
                    // nunca sobe: ignora o que esta acima da altura da cabeca
                    if (pos.getY() > pe.getY() + 1) continue;

                    double score = camadas ? pos.getY() * 1000.0 - d : -d;
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

        // nunca sobe pra quebrar: so conta o que esta na altura da cabeca ou abaixo
        y1 = Math.min(y1, (int) Math.floor(p.posY) + 1);

        BlockPos melhor = null;
        double melhorScore = -Double.MAX_VALUE;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dh = Math.hypot(x + 0.5 - p.posX, z + 0.5 - p.posZ);
                for (int y = y0; y <= y1; y++) {
                    double score = camadas ? y * 1000.0 - dh : -dh;
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

        // define a camada de trabalho: a mais alta (ao seu alcance) que ainda tem bloco
        if (camadas && camadaEscavar == Integer.MIN_VALUE) {
            alvoLonge = acharProximoEscavar(p);
            if (alvoLonge == null) {
                terminouEscavar();
                return;
            }
            camadaEscavar = alvoLonge.getY();
            msg("Camada de trabalho: Y " + camadaEscavar);
        }

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

        // 1) bloco DA CAMADA ATUAL ao alcance: quebra
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
            alvoContagem = alvo;
            ticksParado = 0;
            ultX = p.posX;
            ultZ = p.posZ;
            return;
        }
        atacar(false);

        // 2) nada da camada ao alcance: acha o proximo que falta. Enquanto a camada tiver bloco
        //    longe, anda ate ele (varrendo a camada); so desce quando a camada acabar.
        ticksVarredura++;
        if (alvoLonge == null || ticksVarredura >= 20 || mc.theWorld.isAirBlock(alvoLonge)) {
            ticksVarredura = 0;
            alvoLonge = acharProximoEscavar(p);
            if (alvoLonge == null) {
                terminouEscavar();
                return;
            }
            if (camadas && alvoLonge.getY() != camadaEscavar) {
                camadaEscavar = alvoLonge.getY();
                msg("Camada limpa. Descendo pra Y " + camadaEscavar);
            }
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

        // caminho fechado por um bloco da area (de outra camada): quebra ele pra passar
        if (!livre && !degrau && seguro && dentro && dh > 1.2) {
            BlockPos bloqueio = null;
            if (!passavel(frente.up()) && dentroArea(frente.up()) && escavavel(frente.up())) {
                bloqueio = frente.up();
            } else if (!passavel(frente) && dentroArea(frente) && escavavel(frente)) {
                bloqueio = frente;
            }
            if (bloqueio != null) {
                andar(false);
                boolean alinhado = mirarSuave(p, bloqueio);
                escolherFerramenta(p, bloqueio);
                atacar(alinhado);
                ultimoAlvoEscavar = bloqueio;
                alvoContagem = bloqueio;
                ticksParado = 0;
                ultX = p.posX;
                ultZ = p.posZ;
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

        float dif = MathHelper.wrapAngleTo180_float(yawAlvo - p.rotationYaw);
        suavizar(p);
        // perto demais na horizontal (alvo logo acima/abaixo): nao fica andando a toa
        andar(dh > 1.2 && (livre || degrau) && dentro && Math.abs(dif) < 60.0f);
        pular(degrau || puloAuto);
    }

    private void terminouEscavar() {
        modoEscavar = false; // volta ao modo normal pra proxima vez que ligar
        desligar("Area limpa, terminei. (Blocos encostados em agua/lava e inquebraveis foram pulados.)");
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
        BlockPos minerio = acharMinerio(p, raioBusca(), reach);
        if (minerio != null) {
            boolean alinhado = mirarSuave(p, minerio);
            escolherFerramenta(p, minerio);
            atacar(alinhado);
            alvoContagem = minerio;
            if (alinhado) multiQuebra(p, minerio, false);
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
            alvoContagem = quebrar;
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
            ev.buttonList.add(new GuiButton(BTN_MULTI, 4, 52, 130, 20, textoMulti()));
            ev.buttonList.add(new GuiButton(BTN_GRAVAR, 4, 76, 130, 20, textoGravar()));
            ev.buttonList.add(new GuiButton(BTN_TOCAR, 4, 100, 130, 20, textoTocar()));
            return;
        }
        if (ev.gui instanceof GuiInventory) {
            ev.buttonList.add(new GuiButton(BTN_DROPAR, ev.gui.width / 2 + 92, ev.gui.height / 2 - 83, 72, 20, "Dropar tudo"));
            ev.buttonList.add(new GuiButton(BTN_VENDER, ev.gui.width / 2 + 92, ev.gui.height / 2 - 59, 72, 20, "Vender"));
            ev.buttonList.add(new GuiButton(BTN_TOGGLE, ev.gui.width / 2 + 92, ev.gui.height / 2 - 35, 72, 20, ativo ? "Desligar" : "Ligar mod"));
            ev.buttonList.add(new GuiButton(BTN_MULTI, ev.gui.width / 2 + 92, ev.gui.height / 2 - 11, 72, 20, textoMultiCurto()));
        }
    }

    @SubscribeEvent
    public void aoClicarBotao(GuiScreenEvent.ActionPerformedEvent.Post ev) {
        if (ev.button.id == BTN_GRAVAR && ev.gui instanceof GuiIngameMenu) {
            // comeca/para a gravacao e volta pro jogo (a gravacao nao pega o tempo do menu aberto)
            if (gravando) pararGravacao();
            else iniciarGravacao();
            mc.displayGuiScreen(null);
            return;
        }
        if (ev.button.id == BTN_TOCAR && ev.gui instanceof GuiIngameMenu) {
            if (reproduzindo) pararMacro("Macro parada.");
            else iniciarMacro(0);
            mc.displayGuiScreen(null);
            return;
        }
        if (ev.button.id == BTN_MULTI && (ev.gui instanceof GuiIngameMenu || ev.gui instanceof GuiInventory)) {
            // liga/desliga o multi e deixa a tela aberta, so troca o texto do botao
            alternarMulti(!multiLigado);
            ev.button.displayString = (ev.gui instanceof GuiIngameMenu) ? textoMulti() : textoMultiCurto();
            return;
        }
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
            String t = "AutoMine: LIGADO" + (usarArea ? " | area" : "") + (multiAtivo() ? " | multi " + multi : "")
                    + (limiteBlocos > 0 ? " | " + quebrados + "/" + limiteBlocos : "")
                    + (modoEscavar ? " | escavando" : "");
            mc.fontRendererObj.drawStringWithShadow(t, 4.0f, (float) y, 0x55FF55);
            y += 12;
        }
        if (gravando) {
            mc.fontRendererObj.drawStringWithShadow("MACRO: GRAVANDO " + (macro.size() / 20) + "s", 4.0f, (float) y, 0xFF5555);
            y += 12;
        } else if (reproduzindo) {
            mc.fontRendererObj.drawStringWithShadow("MACRO: TOCANDO (volta " + (macroVoltas + 1) + ")", 4.0f, (float) y, 0x55FF55);
            y += 12;
        }
        desenharLista(y);
    }

    // ------------------------------------------------------------ macro
    // Grava, tick a tick, o que voce faz (olhar, andar, pular, agachar, correr, bater, usar e o
    // slot da hotbar) e repete em loop. Repete os MOVIMENTOS, entao funciona melhor se voce comecar
    // do mesmo lugar e na mesma direcao em que gravou.

    private static final int MACRO_MAX = 36000; // 30 minutos (20 ticks por segundo)
    private static final int T_FRENTE = 1;
    private static final int T_TRAS = 2;
    private static final int T_ESQ = 4;
    private static final int T_DIR = 8;
    private static final int T_PULO = 16;
    private static final int T_AGACHA = 32;
    private static final int T_CORRE = 64;
    private static final int T_BATE = 128;
    private static final int T_USA = 256;

    private static class Frame {
        final float yaw;
        final float pitch;
        final int teclas;
        final int slot;

        Frame(float yaw, float pitch, int teclas, int slot) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.teclas = teclas;
            this.slot = slot;
        }
    }

    private final List<Frame> macro = new ArrayList<Frame>();
    private boolean gravando = false;
    private boolean reproduzindo = false;
    private int macroIdx = 0;
    private int macroVoltas = 0;
    private int macroVezes = 0; // 0 = repete sem parar
    private int macroTeclasAnt = 0;
    private BlockPos macroInicio = null;

    private int lerTeclas() {
        int t = 0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) t |= T_FRENTE;
        if (mc.gameSettings.keyBindBack.isKeyDown()) t |= T_TRAS;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) t |= T_ESQ;
        if (mc.gameSettings.keyBindRight.isKeyDown()) t |= T_DIR;
        if (mc.gameSettings.keyBindJump.isKeyDown()) t |= T_PULO;
        if (mc.gameSettings.keyBindSneak.isKeyDown()) t |= T_AGACHA;
        if (mc.gameSettings.keyBindSprint.isKeyDown()) t |= T_CORRE;
        if (mc.gameSettings.keyBindAttack.isKeyDown()) t |= T_BATE;
        if (mc.gameSettings.keyBindUseItem.isKeyDown()) t |= T_USA;
        return t;
    }

    private void setTecla(KeyBinding k, boolean on) {
        KeyBinding.setKeyBindState(k.getKeyCode(), on);
    }

    private void aplicarTeclas(int t) {
        setTecla(mc.gameSettings.keyBindForward, (t & T_FRENTE) != 0);
        setTecla(mc.gameSettings.keyBindBack, (t & T_TRAS) != 0);
        setTecla(mc.gameSettings.keyBindLeft, (t & T_ESQ) != 0);
        setTecla(mc.gameSettings.keyBindRight, (t & T_DIR) != 0);
        setTecla(mc.gameSettings.keyBindJump, (t & T_PULO) != 0);
        setTecla(mc.gameSettings.keyBindSneak, (t & T_AGACHA) != 0);
        setTecla(mc.gameSettings.keyBindSprint, (t & T_CORRE) != 0);
        setTecla(mc.gameSettings.keyBindAttack, (t & T_BATE) != 0);
        setTecla(mc.gameSettings.keyBindUseItem, (t & T_USA) != 0);
        // o "clique" de verdade (bater em mob, colocar bloco) so conta na hora que aperta
        if ((t & T_BATE) != 0 && (macroTeclasAnt & T_BATE) == 0) {
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        if ((t & T_USA) != 0 && (macroTeclasAnt & T_USA) == 0) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        macroTeclasAnt = t;
    }

    private void soltarMacroTeclas() {
        setTecla(mc.gameSettings.keyBindForward, false);
        setTecla(mc.gameSettings.keyBindBack, false);
        setTecla(mc.gameSettings.keyBindLeft, false);
        setTecla(mc.gameSettings.keyBindRight, false);
        setTecla(mc.gameSettings.keyBindJump, false);
        setTecla(mc.gameSettings.keyBindSneak, false);
        setTecla(mc.gameSettings.keyBindSprint, false);
        setTecla(mc.gameSettings.keyBindAttack, false);
        setTecla(mc.gameSettings.keyBindUseItem, false);
        macroTeclasAnt = 0;
    }

    private void iniciarGravacao() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        if (ativo) desligar("AutoMine DESLIGADO pra gravar a macro.");
        if (reproduzindo) pararMacro("Macro parada.");
        macro.clear();
        macroInicio = new BlockPos(p.posX, p.posY, p.posZ);
        gravando = true;
        msg("GRAVANDO! Faca o que quiser (andar, minerar, pular...). Pra parar: /automine macro parar, tecla G ou botao no menu Esc.");
    }

    private void pararGravacao() {
        gravando = false;
        msg("Gravacao parada: " + macro.size() + " ticks (" + (macro.size() / 20) + "s). Pra repetir: /automine macro tocar, tecla H ou botao no menu Esc.");
    }

    private void gravarFrame(EntityPlayerSP p) {
        if (mc.currentScreen != null) return; // menu aberto nao entra na gravacao
        if (macro.size() >= MACRO_MAX) {
            gravando = false;
            msg("Limite da gravacao (30 min) atingido. Gravacao parada.");
            return;
        }
        macro.add(new Frame(p.rotationYaw, p.rotationPitch, lerTeclas(), p.inventory.currentItem));
    }

    private void iniciarMacro(int vezes) {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        if (gravando) pararGravacao();
        if (macro.isEmpty()) {
            msg("Nao tem macro gravada. Use /automine macro gravar (ou carregar <nome>).");
            return;
        }
        if (ativo) desligar("AutoMine DESLIGADO pra tocar a macro.");
        macroIdx = 0;
        macroVoltas = 0;
        macroVezes = vezes;
        macroTeclasAnt = 0;
        reproduzindo = true;
        msg("Tocando a macro (" + (macro.size() / 20) + "s por volta, "
                + (vezes > 0 ? vezes + " voltas" : "sem parar") + "). Pra parar: tecla H, /automine macro parar ou botao no menu Esc.");
        if (macroInicio != null) {
            double d = Math.sqrt(macroInicio.distanceSq(new BlockPos(p.posX, p.posY, p.posZ)));
            if (d > 2.0) {
                msg("Atencao: voce esta a " + (int) d + " blocos de onde comecou a gravar ("
                        + macroInicio.getX() + " " + macroInicio.getY() + " " + macroInicio.getZ()
                        + "). A macro repete os movimentos, entao pode sair diferente.");
            }
        }
    }

    private void pararMacro(String motivo) {
        reproduzindo = false;
        soltarMacroTeclas();
        msg(motivo);
    }

    private void reproduzirFrame(EntityPlayerSP p) {
        if (mc.currentScreen != null) {
            // menu aberto: pausa a macro (solta as teclas e espera)
            soltarMacroTeclas();
            return;
        }
        if (p.getHealth() < VIDA_MINIMA) {
            pararMacro("Vida baixa, parei a macro.");
            return;
        }
        if (p.isInLava()) {
            pararMacro("Lava! Parei a macro.");
            return;
        }
        if (macro.isEmpty()) {
            pararMacro("A macro esta vazia.");
            return;
        }

        Frame f = macro.get(macroIdx);
        p.rotationYaw = f.yaw;
        p.rotationPitch = f.pitch;
        if (f.slot >= 0 && f.slot < 9) p.inventory.currentItem = f.slot;
        aplicarTeclas(f.teclas);

        macroIdx++;
        if (macroIdx >= macro.size()) {
            macroIdx = 0;
            macroVoltas++;
            if (macroVezes > 0 && macroVoltas >= macroVezes) {
                pararMacro("Macro terminou (" + macroVoltas + " voltas).");
            }
        }
    }

    private String textoGravar() {
        return gravando ? "Parar gravacao" : "Gravar macro";
    }

    private String textoTocar() {
        if (reproduzindo) return "Parar macro";
        return macro.isEmpty() ? "Tocar macro" : "Tocar macro (" + (macro.size() / 20) + "s)";
    }

    private File pastaMacros() {
        return new File(mc.mcDataDir, "automine-macros");
    }

    private String nomeSeguro(String n) {
        return n == null ? "" : n.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private void salvarMacro(String nomeBruto) {
        String nome = nomeSeguro(nomeBruto);
        if (nome.length() == 0) {
            msg("Uso: /automine macro salvar <nome>  (letras, numeros, _ e -)");
            return;
        }
        if (macro.isEmpty()) {
            msg("Nao tem macro gravada pra salvar.");
            return;
        }
        PrintWriter w = null;
        try {
            File dir = pastaMacros();
            dir.mkdirs();
            w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, nome + ".txt")), "UTF-8"));
            for (Frame f : macro) {
                w.println(f.yaw + " " + f.pitch + " " + f.teclas + " " + f.slot);
            }
            msg("Macro salva como \"" + nome + "\" (" + (macro.size() / 20) + "s).");
        } catch (IOException e) {
            msg("Erro ao salvar: " + e.getMessage());
        } finally {
            if (w != null) w.close();
        }
    }

    private void carregarMacro(String nomeBruto) {
        String nome = nomeSeguro(nomeBruto);
        if (nome.length() == 0) {
            msg("Uso: /automine macro carregar <nome>  (veja os nomes com /automine macro lista)");
            return;
        }
        File arq = new File(pastaMacros(), nome + ".txt");
        if (!arq.exists()) {
            msg("Nao achei a macro \"" + nome + "\". Veja /automine macro lista");
            return;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(arq), "UTF-8"));
            List<Frame> lidos = new ArrayList<Frame>();
            String linha;
            while ((linha = r.readLine()) != null && lidos.size() < MACRO_MAX) {
                String[] x = linha.trim().split(" ");
                if (x.length < 4) continue;
                lidos.add(new Frame(Float.parseFloat(x[0]), Float.parseFloat(x[1]),
                        Integer.parseInt(x[2]), Integer.parseInt(x[3])));
            }
            if (reproduzindo) pararMacro("Macro parada.");
            gravando = false;
            macro.clear();
            macro.addAll(lidos);
            macroInicio = null;
            msg("Macro \"" + nome + "\" carregada (" + (macro.size() / 20) + "s). Toque com /automine macro tocar.");
        } catch (IOException e) {
            msg("Erro ao carregar: " + e.getMessage());
        } catch (NumberFormatException e) {
            msg("Arquivo da macro estragado: " + e.getMessage());
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignorado) {
                    // nada a fazer
                }
            }
        }
    }

    private void listarMacros() {
        String[] nomes = pastaMacros().list();
        if (nomes == null || nomes.length == 0) {
            msg("Nenhuma macro salva ainda. Grave uma e use /automine macro salvar <nome>");
            return;
        }
        StringBuilder sb = new StringBuilder("Macros salvas:");
        for (String n : nomes) {
            if (n.endsWith(".txt")) sb.append(' ').append(n.substring(0, n.length() - 4));
        }
        msg(sb.toString());
    }

    // ---------------------------------------------- multi, limite e ajuda

    private boolean multiAtivo() {
        return multiLigado && multi > 1;
    }

    // voce quebrando um bloco (botao de bater apertado olhando pra um bloco) com o multi ligado:
    // manda os pedidos dos blocos de perto junto
    private void multiManual(EntityPlayerSP p) {
        if (p == null || !mc.gameSettings.keyBindAttack.isKeyDown()) return;
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        BlockPos principal = mop.getBlockPos();
        if (principal == null) return;
        multiQuebra(p, principal, true);
    }

    private void alternarMulti(boolean ligar) {
        multiLigado = ligar;
        if (!ligar) cancelarPendentes();
        msg("Multi-quebra " + (ligar ? "LIGADO" : "desligado") + " (" + multi + " blocos)"
                + ((ligar && multi <= 1) ? " - a quantidade esta em 1, use /automine multi 5" : ""));
    }

    private String textoMulti() {
        return multiLigado ? "Multi: LIGADO (" + multi + ")" : "Multi: desligado";
    }

    private String textoMultiCurto() {
        return multiLigado ? "Multi: ON" : "Multi: off";
    }

    // conta o bloco que estava sendo quebrado quando ele some (vira ar)
    private void contarQuebras() {
        if (alvoContagem != null && mc.theWorld.isAirBlock(alvoContagem)) {
            quebrados++;
            alvoContagem = null;
        }
    }

    // linha no chat sem o prefixo [AutoMine] (usada na ajuda)
    private void lin(String s) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(s));
        }
    }

    private void aplicarTurbo() {
        try {
            PlayerControllerMP pc = mc.playerController;
            if (pc == null) return;
            // tira o atraso de 5 ticks entre um bloco e outro
            ReflectionHelper.setPrivateValue(PlayerControllerMP.class, pc, Integer.valueOf(0),
                    "blockHitDelay", "field_78781_i");
            // o servidor aceita a quebra com 70% de progresso: pula os 30% finais
            Float prog = ReflectionHelper.getPrivateValue(PlayerControllerMP.class, pc,
                    "curBlockDamageMP", "field_78770_f");
            if (turboMax && prog != null && prog.floatValue() >= 0.7f && prog.floatValue() < 1.0f) {
                ReflectionHelper.setPrivateValue(PlayerControllerMP.class, pc, Float.valueOf(1.0f),
                        "curBlockDamageMP", "field_78770_f");
            }
        } catch (RuntimeException e) {
            turboFalhou = true;
            msg("Turbo indisponivel nesta versao (" + e.getClass().getSimpleName() + "). Desativado.");
        }
    }

    private void status() {
        String modo = modoEscavar ? "escavar area" : (modoTunel ? "tunel" : "andando");
        lin("§6--- Status do AutoMine ---");
        lin("§fLigado: §e" + (ativo ? "sim" : "nao") + "§f | Modo: §e" + modo);
        lin("§fMinerando: §e" + filtroAtual + "§f | Blocos compactos: §e" + (incluirBlocos ? "sim" : "nao"));
        lin("§fVelocidade: §e" + nivelVel + "§f (1 lento ... 6 maximo) | Nuker: §e" + (nuker ? "LIGADO (" + nukerQuebrados + " quebrados)" : "desligado") + "§f | Reach: §e" + reach + "§f | Doidao: §e" + (doidao ? "sim" : "nao") + "§f | Camadas: §e" + (camadas ? "sim" : "nao") + "§f | Turbo: §e" + (turboFalhou ? "indisponivel" : turbo ? (turboMax ? "MAXIMO" : "LIGADO") : "desligado"));
        lin("§fLimite: §e" + (limiteBlocos == 0 ? "sem limite" : limiteBlocos + " blocos")
                + "§f | Quebrados agora: §e" + quebrados);
        lin("§fMulti: §e" + (multiLigado ? "LIGADO" : "desligado") + "§f | Quantidade: §e" + multi
                + "§f | Extras: §e" + (multiSoIgual ? "mesmo tipo" : "qualquer bloco"));
        lin("§fArea: §e" + descricaoArea());
        lin("§fMacro: §e" + (gravando ? "gravando" : (reproduzindo ? "tocando" : "parada"))
                + "§f | gravada: §e" + (macro.size() / 20) + "s");
        lin("§7Ajuda completa: /automine ajuda");
    }

    private void ajuda(String t) {
        String ouro = "§6", branco = "§f", cinza = "§7", verde = "§a", amarelo = "§e";
        if (t.equals("ligar")) {
            lin(ouro + "--- Ligar e desligar ---");
            lin(branco + "/automine " + cinza + "- liga ou desliga (usa o ultimo modo)");
            lin(branco + "/automine parar " + cinza + "- desliga");
            lin(branco + "Tecla M " + cinza + "- liga ou desliga");
            lin(branco + "Menu Esc " + cinza + "- botao \"Ligar AutoMine\"");
            lin(branco + "Inventario " + cinza + "- botao \"Ligar mod\"");
            lin(cinza + "Enquanto esta ligado aparece um aviso verde no canto da tela.");
        } else if (t.equals("minerar")) {
            lin(ouro + "--- O que minerar ---");
            lin(branco + "/automine tudo " + cinza + "- todos os minerios (padrao)");
            lin(branco + "/automine diamante " + cinza + "- so um minerio. Tambem: ferro, ouro, carvao, redstone, lapis, esmeralda, quartzo");
            lin(branco + "/automine incluirblocos on|off " + cinza + "- tambem quebra os blocos compactos (diamante, ferro...). Padrao: off");
            lin(branco + "/automine blocos " + cinza + "- so os blocos compactos");
            lin(branco + "/automine tunel [minerio] " + cinza + "- cava em linha reta");
            lin(branco + "/automine escavar " + cinza + "- quebra tudo dentro da area (veja: ajuda area)");
        } else if (t.equals("area")) {
            lin(ouro + "--- Area demarcada ---");
            lin(verde + "0. " + branco + "GRAVETO na mao + botao direito no bloco " + cinza + "- marca pos1 e pos2 (/automine varinha off desliga)");
            lin(verde + "1. " + branco + "/automine pos1 " + cinza + "- no primeiro canto");
            lin(verde + "2. " + branco + "/automine pos2 " + cinza + "- no canto oposto (a area liga sozinha)");
            lin(verde + "3. " + branco + "/automine " + cinza + "ou " + branco + "/automine escavar");
            lin(branco + "/automine area on|off|limpar " + cinza + "- liga, desliga ou apaga a area");
            lin(branco + "/automine escavar " + cinza + "- limpa a area CAMADA POR CAMADA: varre a de cima inteira andando e so depois desce");
            lin(cinza + "A altura (Y) conta. Pra escavar, fique dentro da area, no ponto mais alto.");
            lin(cinza + "Com a area ligada ele so mina e so anda dentro dela.");
        } else if (t.equals("ajustes")) {
            lin(ouro + "--- Ajustes ---");
            lin(branco + "/automine velocidade 1-6 " + cinza + "- 1 lento ... 6 maximo (padrao 6)");
            lin(branco + "/automine nuker [on|off|N] " + cinza + "- quebra varios blocos por tick ao alcance (N = blocos/tick, padrao 20)");
            lin(branco + "/automine nuker minerios|tudo " + cinza + "- o que o nuker quebra (padrao: tudo)");
            lin(branco + "/automine nuker forcar on|off " + cinza + "- tenta quebrar tudo de uma vez; so em servidor que nao valida");
            lin(branco + "/automine reach N " + cinza + "- alcance de quebra de 3 a 8 (padrao 4); vale pro automatico, nuker e pro seu clique | reach mouse on|off");
            lin(branco + "/automine doidao on|off " + cinza + "- quebra QUALQUER bloco ao alcance normal do player (padrao off)");
            lin(branco + "/automine camadas on|off " + cinza + "- escavar camada por camada (padrao on); off = quebra o mais perto");
            lin(branco + "/automine turbo on|off " + cinza + "- on = sem atraso entre blocos (padrao) | max = ainda mais rapido, mas pode travar num bloco");
            lin(branco + "/automine limite N " + cinza + "- para depois de quebrar N blocos (0 = sem limite)");
            lin(branco + "/automine multi N " + cinza + "- quantos blocos de uma vez (1 a 500)");
            lin(branco + "/automine multi on|off " + cinza + "- LIGA ou DESLIGA o multi. Comeca desligado");
            lin(branco + "/automine trecho S " + cinza + "- segundos andando numa direcao (5-300)");
            lin(branco + "/automine raio N " + cinza + "- distancia maxima do ponto inicial (10-500)");
            lin(branco + "/automine status " + cinza + "- mostra tudo que esta configurado");
            lin(branco + "/automine multimodo igual|qualquer " + cinza + "- quando VOCE quebra: so blocos do mesmo tipo, ou qualquer um");
            lin(cinza + "O multi so age quando voce liga: comando, tecla N ou botao no Esc/inventario.");
            lin(cinza + "Funciona com o mod minerando E quando voce mesmo quebra um bloco (mod desligado).");
        } else if (t.equals("extras")) {
            lin(ouro + "--- Extras ---");
            lin(amarelo + "Ver players: " + branco + "tecla P, botao no Esc ou /automine players on|off|lista|linhas|alcance N");
            lin(amarelo + "Dropar: " + branco + "botao no inventario, tecla K ou /automine dropar [mochila]");
            lin(amarelo + "Vender: " + branco + "botao no inventario, tecla L ou /automine vender");
            lin(cinza + "/automine vendermodo enviar|abrir - envia /vender direto ou so abre o chat com ele digitado");
        } else if (t.equals("macro")) {
            lin(ouro + "--- Macro (grava o que voce faz e repete) ---");
            lin(verde + "1. " + branco + "/automine macro gravar " + cinza + "- comeca a gravar (ou tecla G, ou botao no Esc)");
            lin(verde + "2. " + cinza + "Ande, minere, pule... faca o que quiser repetir");
            lin(verde + "3. " + branco + "/automine macro parar " + cinza + "- para a gravacao (ou tecla G de novo)");
            lin(verde + "4. " + branco + "/automine macro tocar " + cinza + "- repete em loop (ou tecla H, ou botao no Esc)");
            lin(branco + "/automine macro tocar 5 " + cinza + "- repete so 5 voltas");
            lin(branco + "/automine macro salvar <nome> " + cinza + "- guarda; " + branco + "carregar <nome>" + cinza + " e " + branco + "lista");
            lin(cinza + "Repete os MOVIMENTOS: comece do mesmo lugar e direcao em que gravou.");
            lin(cinza + "Abrir um menu pausa a macro. Para sozinha com vida baixa ou lava.");
        } else if (t.equals("teclas")) {
            lin(ouro + "--- Teclas (mudam em Controles > AutoMine) ---");
            lin(branco + "M " + cinza + "- liga/desliga o mod");
            lin(branco + "N " + cinza + "- liga/desliga o multi");
            lin(branco + "P " + cinza + "- ver players");
            lin(branco + "K " + cinza + "- dropar inventario");
            lin(branco + "L " + cinza + "- vender");
            lin(branco + "G " + cinza + "- macro: gravar / parar gravacao");
            lin(branco + "H " + cinza + "- macro: tocar / parar");
        } else {
            lin(ouro + "=== AutoMine - AJUDA ===");
            lin(cinza + "Escolha um assunto:");
            lin(verde + "/automine ajuda ligar " + cinza + "- ligar e desligar");
            lin(verde + "/automine ajuda minerar " + cinza + "- o que ele minera");
            lin(verde + "/automine ajuda area " + cinza + "- demarcar area e escavar");
            lin(verde + "/automine ajuda ajustes " + cinza + "- velocidade, limite, multi");
            lin(verde + "/automine ajuda extras " + cinza + "- players, dropar, vender");
            lin(verde + "/automine ajuda macro " + cinza + "- gravar o que voce faz e repetir");
            lin(verde + "/automine ajuda teclas " + cinza + "- todas as teclas");
            lin(verde + "/automine status " + cinza + "- ajustes atuais");
        }
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

    // clique direito num bloco segurando um GRAVETO: marca pos1, depois pos2, depois pos1 de novo...
    private boolean varinha = true;
    private boolean proximoEhPos2 = false;

    @SubscribeEvent
    public void aoInteragir(PlayerInteractEvent ev) {
        if (!varinha || ev.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (ev.world == null || !ev.world.isRemote) return;
        ItemStack mao = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
        if (mao == null || mao.getItem() != Items.stick) return;
        ev.setCanceled(true);
        BlockPos b = ev.pos;
        if (b == null) return;
        if (proximoEhPos2) {
            pos2 = b;
            msg("pos2 = " + b.getX() + " " + b.getY() + " " + b.getZ());
        } else {
            pos1 = b;
            msg("pos1 = " + b.getX() + " " + b.getY() + " " + b.getZ());
        }
        proximoEhPos2 = !proximoEhPos2;
        if (pos1 != null && pos2 != null) {
            atualizarArea();
            usarArea = true;
            msg("Area ligada: " + descricaoArea());
        }
    }

    private void caixa(double x0, double y0, double z0, double x1, double y1, double z1) {
        GL11.glBegin(GL11.GL_LINES);
        double[] xs = {x0, x1};
        double[] ys = {y0, y1};
        double[] zs = {z0, z1};
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                GL11.glVertex3d(xs[0], ys[i], zs[j]);
                GL11.glVertex3d(xs[1], ys[i], zs[j]);
                GL11.glVertex3d(xs[i], ys[0], zs[j]);
                GL11.glVertex3d(xs[i], ys[1], zs[j]);
                GL11.glVertex3d(xs[i], ys[j], zs[0]);
                GL11.glVertex3d(xs[i], ys[j], zs[1]);
            }
        }
        GL11.glEnd();
    }

    // linha da mira ate cada player (vermelha = perto, verde = longe), vista atraves das paredes
    @SubscribeEvent
    public void aoRenderMundo(RenderWorldLastEvent ev) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!linhasPlayers && pos1 == null && pos2 == null) return;
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
            if (!linhasPlayers) break;
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

        // marcacao da area: pos1 verde, pos2 vermelho, area inteira amarela
        GL11.glLineWidth(3.0f);
        double e = 0.003;
        if (pos1 != null) {
            GlStateManager.color(0.0f, 1.0f, 0.0f, 1.0f);
            caixa(pos1.getX() - e, pos1.getY() - e, pos1.getZ() - e, pos1.getX() + 1 + e, pos1.getY() + 1 + e, pos1.getZ() + 1 + e);
        }
        if (pos2 != null) {
            GlStateManager.color(1.0f, 0.1f, 0.1f, 1.0f);
            caixa(pos2.getX() - e, pos2.getY() - e, pos2.getZ() - e, pos2.getX() + 1 + e, pos2.getY() + 1 + e, pos2.getZ() + 1 + e);
        }
        if (pos1 != null && pos2 != null) {
            atualizarArea();
            GlStateManager.color(1.0f, 0.9f, 0.0f, 1.0f);
            caixa(aMinX, aMinY, aMinZ, aMaxX + 1, aMaxY + 1, aMaxZ + 1);
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

    // ------------------------------------------------------ nuker

    private static class NK {
        final BlockPos pos;
        final double d;

        NK(BlockPos pos, double d) {
            this.pos = pos;
            this.d = d;
        }
    }

    private void desligarNuker() {
        if (nukerLento != null) {
            enviarDig(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, nukerLento, EnumFacing.UP);
        }
        nukerLento = null;
        nukerEnviado.clear();
        nukerFalhas.clear();
    }

    private void nukerSlot(EntityPlayerSP p) {
        if (nukerSlot != p.inventory.currentItem && mc.getNetHandler() != null) {
            nukerSlot = p.inventory.currentItem;
            mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(nukerSlot));
        }
    }

    private void tickNuker(EntityPlayerSP p) {
        if (mc.currentScreen != null || mc.getNetHandler() == null) return;
        if (p.isInLava() || p.getHealth() < VIDA_MINIMA) return;

        // confere o que foi enviado: sumiu = quebrou; nao sumiu depois de 8 ticks = falhou (ate 3 tentativas)
        for (BlockPos k : new ArrayList<BlockPos>(nukerEnviado.keySet())) {
            int t = nukerEnviado.get(k);
            if (mc.theWorld.isAirBlock(k)) {
                nukerEnviado.remove(k);
                nukerQuebrados++;
            } else if (tickGlobal - t > 8) {
                nukerEnviado.remove(k);
                Integer f = nukerFalhas.get(k);
                nukerFalhas.put(k, f == null ? 1 : f + 1);
            }
        }

        if (limiteBlocos > 0 && nukerQuebrados >= limiteBlocos) {
            nuker = false;
            desligarNuker();
            msg("Nuker parou: limite de " + limiteBlocos + " blocos.");
            return;
        }

        Vec3 olhos = new Vec3(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos base = new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos pe = new BlockPos(p.posX, p.posY, p.posZ);
        double alc = Math.min(8.0, Math.max(reach, 4.5));
        int r = (int) Math.ceil(alc);

        List<NK> lista = new ArrayList<NK>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    Block b = mc.theWorld.getBlockState(pos).getBlock();
                    if (b == Blocks.air) continue;
                    if (nukerSoMinerio && !minerios.contains(b)) continue;
                    if (usarArea && !dentroArea(pos)) continue;
                    if (nukerEnviado.containsKey(pos)) continue;
                    Integer f = nukerFalhas.get(pos);
                    if (f != null && f >= 3) continue;
                    double d = olhos.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    if (d > alc) continue;
                    if (!escavavel(pos)) continue;
                    if (pos.equals(pe.down()) && !chaoSeguro(pe)) continue;
                    lista.add(new NK(pos, d));
                }
            }
        }
        if (lista.isEmpty()) {
            if (nukerLento != null) {
                enviarDig(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, nukerLento, EnumFacing.UP);
                nukerLento = null;
            }
            return;
        }
        Collections.sort(lista, new Comparator<NK>() {
            @Override
            public int compare(NK a, NK b) {
                return Double.compare(a.d, b.d);
            }
        });

        int budget = nukerQtd;
        if (limiteBlocos > 0) budget = Math.min(budget, limiteBlocos - nukerQuebrados - nukerEnviado.size());
        if (budget <= 0) return;

        int feitos = 0;
        for (NK n : lista) {
            if (feitos >= budget) break;
            BlockPos pos = n.pos;
            Block b = mc.theWorld.getBlockState(pos).getBlock();
            escolherFerramenta(p, pos);
            nukerSlot(p);
            float h = b.getPlayerRelativeBlockHardness(p, mc.theWorld, pos);
            if (nukerForcar || h >= 0.7f) {
                EnumFacing face = faceParaJogador(p, pos);
                enviarDig(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, face);
                enviarDig(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, face);
                nukerEnviado.put(pos, tickGlobal);
                feitos++;
            }
        }
        if (feitos > 0) {
            nukerLento = null; // o servidor so acompanha um bloco: o START acima trocou o alvo dele
            p.swingItem();
            return;
        }

        // nenhum bloco instantaneo: trabalha no mais perto, respeitando o tempo de quebra do servidor
        BlockPos alvo = null;
        if (nukerLento != null) {
            for (NK n : lista) {
                if (n.pos.equals(nukerLento)) {
                    alvo = nukerLento;
                    break;
                }
            }
        }
        if (alvo == null) alvo = lista.get(0).pos;
        Block b = mc.theWorld.getBlockState(alvo).getBlock();
        escolherFerramenta(p, alvo);
        nukerSlot(p);
        EnumFacing face = faceParaJogador(p, alvo);
        if (!alvo.equals(nukerLento)) {
            nukerLento = alvo;
            nukerTicks = 0;
            enviarDig(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, alvo, face);
        } else {
            nukerTicks++;
            float h = Math.max(0.001f, b.getPlayerRelativeBlockHardness(p, mc.theWorld, alvo));
            int precisa = (int) Math.ceil(0.7f / h);
            if (nukerTicks >= precisa) {
                enviarDig(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, alvo, face);
                nukerEnviado.put(alvo, tickGlobal);
                nukerLento = null;
            }
        }
        p.swingItem();
    }

    private EnumFacing faceParaJogador(EntityPlayerSP p, BlockPos pos) {
        return EnumFacing.getFacingFromVector(
                (float) (p.posX - (pos.getX() + 0.5)),
                (float) (p.posY + p.getEyeHeight() - (pos.getY() + 0.5)),
                (float) (p.posZ - (pos.getZ() + 0.5)));
    }

    // ------------------------------------------------------ multi-quebra

    private void enviarDig(C07PacketPlayerDigging.Action acao, BlockPos pos, EnumFacing face) {
        if (mc.getNetHandler() != null) {
            mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(acao, pos, face));
        }
    }

    // manda START_DESTROY_BLOCK de ate (multi - 1) blocos extras visiveis ao alcance;
    // depois do tempo de quebra manda o STOP (ver processarPendentes)
    // manual = true: voce esta quebrando o bloco "principal" com o mouse/toque; os extras sao os
    // blocos mais perto dele (do mesmo tipo, ou qualquer um, conforme /automine multimodo)
    private void multiQuebra(EntityPlayerSP p, BlockPos principal, boolean manual) {
        if (!multiAtivo()) return;
        Block blocoPrincipal = mc.theWorld.getBlockState(principal).getBlock();
        int faltam = (multi - 1) - pendentes.size();
        if (limiteBlocos > 0 && !manual) {
            // nao deixa os extras passarem do limite (1 = o bloco principal)
            int resta = limiteBlocos - quebrados - 1 - pendentes.size();
            if (resta < faltam) faltam = Math.max(0, resta);
        }
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
                    if (manual) {
                        if (b == Blocks.air || b.getMaterial().isLiquid()
                                || b.getBlockHardness(mc.theWorld, pos) < 0.0f) continue;
                        if (multiSoIgual && b != blocoPrincipal) continue;
                    } else {
                        if (!minerios.contains(b)) continue;
                        if (usarArea && !dentroArea(pos)) continue;
                    }

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
                        // manual: ordena pelos mais colados no bloco que voce quebra
                        achados.add(new Alvo(pos, r.sideHit, manual ? pos.distanceSq(principal) : d));
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
            pendentes.add(new Pendente(a.pos, a.face, ticks, b));
            Integer t = tentativas.get(a.pos);
            tentativas.put(a.pos, t == null ? 1 : t + 1);
        }
    }

    private void processarPendentes() {
        for (int i = pendentes.size() - 1; i >= 0; i--) {
            Pendente pd = pendentes.get(i);
            Block b = mc.theWorld.getBlockState(pd.pos).getBlock();
            if (b != pd.tipo) {
                // ja quebrou (ou sumiu)
                quebrados++;
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

        // doidao: qualquer bloco no alcance normal de um player (4.5), so na busca de perto
        boolean qualquer = doidao && alcance < ALCANCE_LONGE;
        if (qualquer) {
            alcance = Math.max(alcance, 4.5);
            raio = (int) Math.ceil(alcance);
        }
        BlockPos pe = new BlockPos(p.posX, p.posY, p.posZ);

        for (int dx = -raio; dx <= raio; dx++) {
            for (int dy = -raio; dy <= raio; dy++) {
                for (int dz = -raio; dz <= raio; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    Block b = mc.theWorld.getBlockState(pos).getBlock();
                    if (qualquer) {
                        if (!escavavel(pos)) continue;
                        if (pos.equals(pe.down()) && !chaoSeguro(pe)) continue;
                    } else if (!minerios.contains(b)) continue;
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
            return "/automine ajuda  (lista os comandos por assunto)";
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
            } else if (a.equals("turbo")) {
                String t = args.length > 1 ? args[1].toLowerCase() : "on";
                if (t.equals("off")) turbo = false;
                else {
                    turbo = true;
                    turboFalhou = false;
                    turboMax = t.equals("max");
                }
                msg("Turbo: " + (!turbo ? "desligado" : turboMax ? "MAXIMO (se ficar preso num bloco, use /automine turbo on)" : "LIGADO"));
            } else if (a.equals("macro")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("gravar")) {
                    iniciarGravacao();
                } else if (o.equals("parar")) {
                    if (gravando) pararGravacao();
                    else if (reproduzindo) pararMacro("Macro parada.");
                    else msg("Nao tem macro gravando nem tocando.");
                } else if (o.equals("tocar")) {
                    int vezes = 0;
                    if (args.length > 2) {
                        try {
                            vezes = Integer.parseInt(args[2]);
                        } catch (NumberFormatException ex) {
                            vezes = 0;
                        }
                    }
                    iniciarMacro(Math.max(0, vezes));
                } else if (o.equals("salvar")) {
                    salvarMacro(args.length > 2 ? args[2] : "");
                } else if (o.equals("carregar")) {
                    carregarMacro(args.length > 2 ? args[2] : "");
                } else if (o.equals("lista")) {
                    listarMacros();
                } else {
                    msg("Macro: " + (gravando ? "GRAVANDO" : (reproduzindo ? "TOCANDO" : "parada"))
                            + " | gravada: " + (macro.size() / 20) + "s");
                    msg("Uso: /automine macro gravar | parar | tocar [voltas] | salvar <nome> | carregar <nome> | lista");
                }
            } else if (a.equals("ajuda") || a.equals("help") || a.equals("?")) {
                ajuda(args.length > 1 ? args[1].toLowerCase() : "");
            } else if (a.equals("status")) {
                status();
            } else if (a.equals("limite")) {
                if (args.length > 1) {
                    int n = -1;
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        n = -1;
                    }
                    if (n >= 0 && n <= 10000000) {
                        limiteBlocos = n;
                        msg(n == 0 ? "Limite desligado (sem limite)."
                                : "Limite: para depois de quebrar " + n + " blocos (conta de novo a cada vez que voce liga).");
                    } else {
                        msg("Uso: /automine limite <numero>  (0 = sem limite)");
                    }
                } else {
                    msg("Limite: " + (limiteBlocos == 0 ? "sem limite" : limiteBlocos + " blocos")
                            + " | quebrados nesta rodada: " + quebrados);
                    msg("Uso: /automine limite <numero>  (0 = sem limite)");
                }
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
            } else if (a.equals("nuker")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                boolean novo = !nuker;
                if (o.equals("on")) novo = true;
                else if (o.equals("off")) novo = false;
                else if (o.equals("forcar")) {
                    nukerForcar = args.length > 2 ? !args[2].equalsIgnoreCase("off") : !nukerForcar;
                    msg("Nuker forcar: " + (nukerForcar ? "LIGADO (manda START+STOP em tudo; so funciona se o servidor nao validar)" : "desligado"));
                    return;
                } else if (o.equals("minerios") || o.equals("tudo")) {
                    nukerSoMinerio = o.equals("minerios");
                    msg("Nuker quebra: " + (nukerSoMinerio ? "so minerios" : "qualquer bloco"));
                    return;
                } else if (!o.isEmpty()) {
                    try {
                        nukerQtd = Math.max(1, Math.min(200, Integer.parseInt(o)));
                        novo = true;
                    } catch (NumberFormatException ex) {
                        msg("Uso: /automine nuker [on|off|N|forcar on|off|minerios|tudo]");
                        return;
                    }
                }
                if (novo && !nuker) {
                    nukerQuebrados = 0;
                    nukerEnviado.clear();
                    nukerFalhas.clear();
                    nukerLento = null;
                }
                if (!novo && nuker) desligarNuker();
                nuker = novo;
                msg("NUKER " + (nuker ? "LIGADO" : "desligado") + " | ate " + nukerQtd + " blocos instantaneos por tick | alcance "
                        + Math.min(8.0, Math.max(reach, 4.5)));
            } else if (a.equals("reach")) {
                if (args.length > 1 && args[1].equalsIgnoreCase("mouse")) {
                    reachMouse = args.length > 2 ? !args[2].equalsIgnoreCase("off") : !reachMouse;
                    msg("Reach no seu clique/toque: " + (reachMouse ? "LIGADO" : "desligado"));
                    return;
                }
                if (args.length > 1) {
                    try {
                        double v = Double.parseDouble(args[1].replace(',', '.'));
                        reach = Math.max(3.0, Math.min(8.0, v));
                    } catch (NumberFormatException ex) {
                        msg("Uso: /automine reach <3 a 6>");
                    }
                }
                msg("Reach: " + reach + (reach > 4.4 ? " (acima de 4.4 vale tambem pro SEU clique; servidor vanilla recusa acima de 6)" : ""));
            } else if (a.equals("doidao")) {
                if (args.length > 1) doidao = !args[1].equalsIgnoreCase("off");
                else doidao = !doidao;
                msg("Modo DOIDAO: " + (doidao ? "LIGADO (quebra qualquer bloco ao alcance)" : "desligado (so minerios)"));
            } else if (a.equals("camadas")) {
                if (args.length > 1) camadas = !args[1].equalsIgnoreCase("off");
                else camadas = !camadas;
                camadaEscavar = Integer.MIN_VALUE;
                msg("Escavar camada por camada: " + (camadas ? "LIGADO" : "desligado (quebra o que estiver mais perto)"));
            } else if (a.equals("varinha")) {
                varinha = !(args.length > 1 && args[1].equalsIgnoreCase("off"));
                msg("Varinha (graveto + botao direito no bloco): " + (varinha ? "LIGADA" : "desligada"));
            } else if (a.equals("area")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("limpar")) {
                    pos1 = null;
                    pos2 = null;
                    proximoEhPos2 = false;
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
            } else if (a.equals("multimodo")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("igual")) {
                    multiSoIgual = true;
                    msg("Multi (quando VOCE quebra): so blocos do mesmo tipo do que voce quebra.");
                } else if (o.equals("qualquer")) {
                    multiSoIgual = false;
                    msg("Multi (quando VOCE quebra): qualquer bloco por perto.");
                } else {
                    msg("Multimodo: " + (multiSoIgual ? "igual" : "qualquer") + ". Uso: /automine multimodo igual | qualquer");
                }
            } else if (a.equals("multi")) {
                String o = args.length > 1 ? args[1].toLowerCase() : "";
                if (o.equals("on") || o.equals("ligar")) {
                    alternarMulti(true);
                } else if (o.equals("off") || o.equals("desligar")) {
                    alternarMulti(false);
                } else if (o.length() > 0) {
                    int n = -1;
                    try {
                        n = Integer.parseInt(o);
                    } catch (NumberFormatException ex) {
                        n = -1;
                    }
                    if (n >= 1 && n <= MULTI_MAX) {
                        multi = n;
                        msg("Quantidade do multi: " + n + " blocos. O multi esta "
                                + (multiLigado ? "LIGADO." : "DESLIGADO - ligue com /automine multi on, tecla N ou botao no Esc."));
                    } else {
                        msg("Uso: /automine multi <1-" + MULTI_MAX + "> (quantidade) | multi on | multi off");
                    }
                } else {
                    msg("Multi: " + (multiLigado ? "LIGADO" : "desligado") + ", quantidade " + multi);
                    msg("Uso: /automine multi <1-" + MULTI_MAX + "> (quantidade) | multi on | multi off");
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
                    msg("Minerio desconhecido. Digite /automine ajuda minerar");
                }
            } else if (definirFiltro(a)) {
                modoTunel = false;
                modoEscavar = false;
                ligar();
                msg("Minerando: " + a);
            } else {
                msg("Comando desconhecido. Digite /automine ajuda");
            }
        }
    }
}
