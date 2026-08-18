package miau.util.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class ShaderUtil {

  private static final String DEFAULT_VERTEX =
      "#version 120\n"
          + "void main() {\n"
          + "    gl_TexCoord[0] = gl_MultiTexCoord0;\n"
          + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
          + "}";

  private final Map<String, Integer> uniforms = new HashMap<>();
  private int programID;

  public ShaderUtil(String fragmentSource) {
    this(DEFAULT_VERTEX, fragmentSource);
  }

  public ShaderUtil(String vertexSource, String fragmentSource) {
    int program = 0;
    try {
      int vert = createShader(vertexSource, GL20.GL_VERTEX_SHADER);
      int frag = createShader(fragmentSource, GL20.GL_FRAGMENT_SHADER);
      if (vert == 0 || frag == 0) {
        this.programID = 0;
        return;
      }
      program = GL20.glCreateProgram();
      GL20.glAttachShader(program, vert);
      GL20.glAttachShader(program, frag);
      GL20.glLinkProgram(program);
      if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
        program = 0;
      }
      GL20.glValidateProgram(program);
      GL20.glDeleteShader(vert);
      GL20.glDeleteShader(frag);
    } catch (Exception e) {
      program = 0;
    }
    this.programID = program;
  }

  private int createShader(String src, int type) {
    try {
      int id = GL20.glCreateShader(type);
      GL20.glShaderSource(id, src);
      GL20.glCompileShader(id);
      if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
        GL20.glDeleteShader(id);
        return 0;
      }
      return id;
    } catch (Exception e) {
      return 0;
    }
  }

  public void init() {
    if (programID != 0) OpenGlHelper.glUseProgram(programID);
  }

  public void unload() {
    if (programID != 0) OpenGlHelper.glUseProgram(0);
  }

  public void setUniformf(String name, float... args) {
    if (programID == 0) return;
    int u = getUniform(name);
    if (u == -1) return;
    switch (args.length) {
      case 1:
        GL20.glUniform1f(u, args[0]);
        break;
      case 2:
        GL20.glUniform2f(u, args[0], args[1]);
        break;
      case 3:
        GL20.glUniform3f(u, args[0], args[1], args[2]);
        break;
      case 4:
        GL20.glUniform4f(u, args[0], args[1], args[2], args[3]);
        break;
    }
  }

  public int getUniform(String name) {
    if (programID == 0) return -1;
    return uniforms.computeIfAbsent(name, n -> GL20.glGetUniformLocation(programID, n));
  }

  public int getProgramID() {
    return programID;
  }

  public static void drawFullscreenQuad(int w, int h) {
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(0, 0);
    GL11.glVertex2f(w, 0);
    GL11.glVertex2f(w, h);
    GL11.glVertex2f(0, h);
    GL11.glEnd();
  }
}
