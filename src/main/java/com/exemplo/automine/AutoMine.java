package com.exemplo.automine;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;
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
 *                               "lapis" e "tudo" tambem quebram o BLOCO de lapis-lazuli;
 *                               "blocolapis" quebra so o bloco (sem o minerio)
 *   /automine tunel [minerio] -> modo tunel: cava em linha reta (quebra tudo na frente)
 *   /automine trecho 30      -> segundos andando numa direcao antes de trocar (padrao 30)
 *   /automine raio 64        -> distancia maxima do ponto onde voce ligou (padrao 64)
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

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        teclaToggle = new KeyBinding("Ligar/desligar AutoMine", Keyboard.KEY_M, "AutoMine");
        ClientRegistry.registerKeyBinding(teclaToggle);
        ClientCommandHandler.instance.registerCommand(new CommandAutoMine());
        MinecraftForge.EVENT_BUS.register(this);
        definirFiltro("tudo");
    }

    // ---------------------------------------------------------------- filtro

    private boolean definirFiltro(String nome) {
        Set<Block> novo = new HashSet<Block>();
        switch (nome) {
            case "diamante": novo.add(Blocks.diamond_ore); break;
            case "ferro": novo.add(Blocks.iron_ore); break;
            case "ouro": novo.add(Blocks.gold_ore); break;
            case "carvao": novo.add(Blocks.coal_ore); break;
            case "redstone": novo.add(Blocks.redstone_ore); novo.add(Blocks.lit_redstone_ore); break;
            case "lapis": novo.add(Blocks.lapis_ore); novo.add(Blocks.lapis_block); break;
            case "blocolapis": novo.add(Blocks.lapis_block); break;
            case "esmeralda": novo.add(Blocks.emerald_ore); break;
            case "quartzo": novo.add(Blocks.quartz_ore); break;
            case "tudo":
                novo.add(Blocks.diamond_ore); novo.add(Blocks.iron_ore); novo.add(Blocks.gold_ore);
                novo.add(Blocks.coal_ore); novo.add(Blocks.redstone_ore); novo.add(Blocks.lit_redstone_ore);
                novo.add(Blocks.lapis_ore); novo.add(Blocks.lapis_block);
                novo.add(Blocks.emerald_ore); novo.add(Blocks.quartz_ore);
                break;
            default: return false;
        }
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
        alvoLonge = null;
        alvoMinerado = null;
        coletaPos = null;
        ultX = p.posX;
        ultZ = p.posZ;
        ativo = true;
        msg("AutoMine LIGADO (" + (modoTunel ? "tunel, direcao " + direcao.getName() : "andando") + ")");
    }

    private void desligar(String motivo) {
        ativo = false;
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
            return;
        }

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

        if (modoTunel) tickTunel(p);
        else tickAndar(p);
    }

    // ------------------------------------------------------ modo andando

    private void tickAndar(EntityPlayerSP p) {
        if (p.isInLava()) {
            desligar("Lava! Parei.");
            return;
        }

        // 1) minerio ao alcance: para e quebra
        BlockPos minerio = acharMinerio(p, RAIO_BUSCA, ALCANCE);
        if (minerio != null) {
            andar(false);
            pular(puloAuto);
            mirar(p, minerio);
            escolherFerramenta(p, minerio);
            atacar(true);
            alvoMinerado = minerio;
            ticksParado = 0;
            return;
        }
        atacar(false);

        // acabou de quebrar um: vai ate onde ele estava pra pegar o drop
        if (alvoMinerado != null) {
            coletaPos = alvoMinerado;
            coletaTicks = 25;
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
            if (alvoLonge != null) {
                yawAlvo = yawPara(p, alvoLonge);
            } else if (distOrigem > raioPasseio) {
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

        if (liquido || buraco) {
            bloqueado = true;
        } else if (!passavel(frente)) {
            if (passavel(frente.up()) && passavel(frente.up(2)) && passavel(pe.up(2))) degrau = true;
            else bloqueado = true;
        } else if (!passavel(frente.up())) {
            bloqueado = true;
        }

        if (bloqueado) {
            virar(90, 180);
            ticksVirando = 8;
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
            ticksVirando = 8;
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
        float dif = MathHelper.wrapAngleTo180_float(yawAlvo - p.rotationYaw);
        p.rotationYaw += Math.max(-25.0f, Math.min(25.0f, dif));
        p.rotationPitch += (0.0f - p.rotationPitch) * 0.3f;
    }

    private float yawPara(EntityPlayerSP p, BlockPos pos) {
        double dx = pos.getX() + 0.5 - p.posX;
        double dz = pos.getZ() + 0.5 - p.posZ;
        return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
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

        // 1) minerio visivel ao alcance
        BlockPos minerio = acharMinerio(p, RAIO_BUSCA, ALCANCE);
        if (minerio != null) {
            mirar(p, minerio);
            escolherFerramenta(p, minerio);
            atacar(true);
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
            mirar(p, quebrar);
            escolherFerramenta(p, quebrar);
            atacar(true);
            andar(false);
        } else {
            // caminho livre: confere se nao tem buraco antes de andar
            if (mc.theWorld.isAirBlock(frentePe.down()) && mc.theWorld.isAirBlock(frentePe.down().down())) {
                desligar("Buraco a frente, parei.");
                return;
            }
            p.rotationYaw = direcao.getHorizontalIndex() * 90.0f;
            p.rotationPitch = 0.0f;
            atacar(false);
            andar(true);
        }
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

    private void mirar(EntityPlayerSP p, BlockPos pos) {
        double dx = pos.getX() + 0.5 - p.posX;
        double dy = pos.getY() + 0.5 - (p.posY + p.getEyeHeight());
        double dz = pos.getZ() + 0.5 - p.posZ;
        double distH = Math.sqrt(dx * dx + dz * dz);
        p.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        p.rotationPitch = (float) -(Math.atan2(dy, distH) * 180.0 / Math.PI);
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
            return "/automine [tudo|diamante|ferro|ouro|carvao|redstone|lapis|blocolapis|esmeralda|quartzo|parar] ou /automine tunel [minerio]";
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
            } else if (a.equals("trecho") || a.equals("raio")) {
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
                } else {
                    msg("Uso: /automine trecho <5-300 segundos> | /automine raio <10-500 blocos>");
                }
            } else if (a.equals("tunel")) {
                String f = args.length > 1 ? args[1].toLowerCase() : "tudo";
                if (definirFiltro(f)) {
                    modoTunel = true;
                    ligar();
                    msg("Minerando: " + f);
                } else {
                    msg("Uso: " + getCommandUsage(sender));
                }
            } else if (definirFiltro(a)) {
                modoTunel = false;
                ligar();
                msg("Minerando: " + a);
            } else {
                msg("Uso: " + getCommandUsage(sender));
            }
        }
    }
}
