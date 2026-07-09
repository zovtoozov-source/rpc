package tech.onetap.util.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import tech.onetap.util.IMinecraft;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class GlProgram implements IMinecraft {
    private static final List<Runnable> REGISTERED_PROGRAMS = new ArrayList<>();

    protected ShaderProgram backingProgram;
    protected ShaderProgramKey programKey;
    private boolean ready;
    private boolean failed;
    private String lastError;

    public GlProgram(Identifier id, VertexFormat vertexFormat) {
        this.programKey = new ShaderProgramKey(Identifier.of(id.getNamespace(), "core/" + id.getPath()), vertexFormat, Defines.EMPTY);
        REGISTERED_PROGRAMS.add(() -> {
            this.ready = false;
            this.failed = false;
            this.lastError = null;

            try {
                ShaderProgram program = RenderSystem.setShader(this.programKey);
                if (program == null) {
                    this.failed = true;
                    this.lastError = "Shader program not found: " + this.programKey;
                    return;
                }

                this.backingProgram = program;
                this.setup();
                this.ready = true;
            } catch (Throwable e) {
                this.failed = true;
                this.lastError = e.getMessage();
                this.backingProgram = null;
            }
        });
    }

    public ShaderProgram use() {
        return RenderSystem.setShader(this.programKey);
    }

    protected void setup() {
    }

    public Uniform findUniform(String name) {
        if (this.backingProgram == null) {
            return null;
        } else {
            return this.backingProgram.getUniform(name);
        }
    }

    public boolean isReady() {
        return this.ready && this.backingProgram != null;
    }

    public boolean isFailed() {
        return this.failed;
    }

    public String getLastError() {
        return this.lastError;
    }

    public static void loadAndSetupPrograms() {
        REGISTERED_PROGRAMS.forEach(Runnable::run);
    }
}
