package com.exemplo.automine;

import java.util.HashSet;
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
 *   /automine                -> liga/desliga (todos os minerios)
 *   /automine diamante       -> liga so para um minerio
 *                               (tudo, diamante, ferro, ouro, carvao, redstone, lapis, esmeralda, quartzo)
 *                               "lapis" e "tudo" tambem quebram o BLOCO de lapis-lazuli;
 *                               "blocolapis" quebra so o bloco (sem o minerio)
 *   /automine parar          -> desliga
 * Tecla M (configuravel em Controles) tambem liga/desliga.
 *
 * O que faz: cava um tunel reto na direcao em que voce estava olhando ao ligar,
 * e quebra qualquer minerio visivel ao alcance (4 blocos). Para sozinho se
 * encontrar liquido, buraco, bloco indestrutivel ou se a vida ficar baixa.
 */
@Mod(modid = AutoMine.MODID, name = "AutoMine", version = "1.0",
        acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*")
public class AutoMine {
    public static final String MODID = "automine";

    private static final double ALCANCE = 4.0;
    private static final int RAIO_BUSCA = 4;
    private static final float VIDA_MINIMA = 8.0f;

    private final Minecraft mc = Minecraft.getMinecraft();
    private KeyBinding teclaToggle;
    private boolean ativo = false;
    private EnumFacing direcao = EnumFacing.NORTH;
    private final Set<Block> minerios = new HashSet<Block>();

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
        if (mc.thePlayer == null) return;
        direcao = mc.thePlayer.getHorizontalFacing();
        ativo = true;
        msg("AutoMine LIGADO (direcao: " + direcao.getName() + ")");
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
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
    }

    private void atacar(boolean on) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), on);
    }

    private void andar(boolean on) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), on);
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
            if (mc.theWorld.getBlockState(b).getBlock().getMaterial().isLiquid()) {
                desligar("Liquido a frente, parei.");
                return;
            }
        }

        // 1) minerio visivel ao alcance
        BlockPos minerio = acharMinerio(p);
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

    private float dureza(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock().getBlockHardness(mc.theWorld, pos);
    }

    private BlockPos acharMinerio(EntityPlayerSP p) {
        Vec3 olhos = new Vec3(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos base = new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        BlockPos melhor = null;
        double melhorDist = Double.MAX_VALUE;

        for (int dx = -RAIO_BUSCA; dx <= RAIO_BUSCA; dx++) {
            for (int dy = -RAIO_BUSCA; dy <= RAIO_BUSCA; dy++) {
                for (int dz = -RAIO_BUSCA; dz <= RAIO_BUSCA; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    Block b = mc.theWorld.getBlockState(pos).getBlock();
                    if (!minerios.contains(b)) continue;

                    Vec3 centro = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double d = olhos.distanceTo(centro);
                    if (d > ALCANCE || d >= melhorDist) continue;

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
            return "/automine [tudo|diamante|ferro|ouro|carvao|redstone|lapis|blocolapis|esmeralda|quartzo|parar]";
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
            } else if (definirFiltro(a)) {
                ligar();
                msg("Minerando: " + a);
            } else {
                msg("Uso: " + getCommandUsage(sender));
            }
        }
    }
}
