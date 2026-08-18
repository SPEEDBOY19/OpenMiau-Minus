package miau.util.demise;

import static org.lwjgl.opengl.GL11.*;

public class DemiseShaderUtil {
  private final ShaderUtils shader;

  public DemiseShaderUtil(String fragmentShader) {
    this.shader = new ShaderUtils(fragmentShader);
  }

  public void init() {
    shader.init();
  }

  public void unload() {
    shader.unload();
  }

  public void setUniformf(String name, float... values) {
    shader.setUniformf(name, values);
  }

  public void setUniformi(String name, int... values) {
    shader.setUniformi(name, values);
  }

  public static void drawQuads(float x, float y, float width, float height) {
    glBegin(GL_QUADS);
    glTexCoord2f(0, 0);
    glVertex2f(x, y);
    glTexCoord2f(0, 1);
    glVertex2f(x, y + height);
    glTexCoord2f(1, 1);
    glVertex2f(x + width, y + height);
    glTexCoord2f(1, 0);
    glVertex2f(x + width, y);
    glEnd();
  }
}
