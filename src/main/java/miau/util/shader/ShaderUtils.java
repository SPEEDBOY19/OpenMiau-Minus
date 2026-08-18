package miau.util.shader;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL20;

public class ShaderUtils {
  private final Minecraft mc = Minecraft.getMinecraft();
  public final int programID;
  private final Map<String, Integer> uniformLocations = new HashMap<>();

  private static final String KAWASE_UP_GLOW =
      "#version 120\n"
          + "uniform sampler2D inTexture, textureToCheck;\n"
          + "uniform vec2 halfpixel, offset, iResolution;\n"
          + "uniform bool check;\n"
          + "uniform float lastPass;\n"
          + "uniform float exposure;\n"
          + "void main() {\n"
          + "    if(check && texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;\n"
          + "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n"
          + "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n"
          + "    sum.rgb *= sum.a;\n"
          + "    vec4 smpl1 = texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);\n"
          + "    smpl1.rgb *= smpl1.a;\n"
          + "    sum += smpl1 * 2.0;\n"
          + "    vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n"
          + "    smp2.rgb *= smp2.a;\n"
          + "    sum += smp2;\n"
          + "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);\n"
          + "    smp3.rgb *= smp3.a;\n"
          + "    sum += smp3 * 2.0;\n"
          + "    vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n"
          + "    smp4.rgb *= smp4.a;\n"
          + "    sum += smp4;\n"
          + "    vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n"
          + "    smp5.rgb *= smp5.a;\n"
          + "    sum += smp5 * 2.0;\n"
          + "    vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n"
          + "    smp6.rgb *= smp6.a;\n"
          + "    sum += smp6;\n"
          + "    vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);\n"
          + "    smp7.rgb *= smp7.a;\n"
          + "    sum += smp7 * 2.0;\n"
          + "    vec4 result = sum / 12.0;\n"
          + "    gl_FragColor = vec4(result.rgb / result.a, mix(result.a, 1.0 - exp(-result.a * exposure), step(0.0, lastPass)));\n"
          + "}";

  private static final String GLOW_SHADER =
      "#version 120\n"
          + "uniform sampler2D textureIn, textureToCheck;\n"
          + "uniform vec2 texelSize, direction;\n"
          + "uniform vec3 color;\n"
          + "uniform bool avoidTexture;\n"
          + "uniform float exposure, radius;\n"
          + "uniform float weights[256];\n"
          + "#define offset direction * texelSize\n"
          + "void main() {\n"
          + "    if (direction.y == 1 && avoidTexture) {\n"
          + "        if (texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;\n"
          + "    }\n"
          + "    vec4 innerColor = texture2D(textureIn, gl_TexCoord[0].st);\n"
          + "    innerColor.rgb *= innerColor.a;\n"
          + "    innerColor *= weights[0];\n"
          + "    for (float r = 1.0; r <= radius; r++) {\n"
          + "        vec4 colorCurrent1 = texture2D(textureIn, gl_TexCoord[0].st + offset * r);\n"
          + "        vec4 colorCurrent2 = texture2D(textureIn, gl_TexCoord[0].st - offset * r);\n"
          + "        colorCurrent1.rgb *= colorCurrent1.a;\n"
          + "        colorCurrent2.rgb *= colorCurrent2.a;\n"
          + "        innerColor += (colorCurrent1 + colorCurrent2) * weights[int(r)];\n"
          + "    }\n"
          + "    gl_FragColor = vec4(innerColor.rgb / innerColor.a, mix(innerColor.a, 1.0 - exp(-innerColor.a * exposure), step(0.0, direction.y)));\n"
          + "}";

  private static final String CHAMS =
      "#version 120\n"
          + "uniform sampler2D textureIn;\n"
          + "uniform vec4 color;\n"
          + "void main() {\n"
          + "    float alpha = texture2D(textureIn, gl_TexCoord[0].st).a;\n"
          + "    gl_FragColor = vec4(color.rgb, color.a * mix(0.0, alpha, step(0.0, alpha)));\n"
          + "}";

  private static final String KAWASE_UP_BLOOM =
      "#version 120\n"
          + "uniform sampler2D inTexture, textureToCheck;\n"
          + "uniform vec2 halfpixel, offset, iResolution;\n"
          + "uniform int check;\n"
          + "void main() {\n"
          + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
          + "    vec2 offset1 = vec2(-halfpixel.x, 0.0) * offset;\n"
          + "    vec2 offset2 = vec2(-halfpixel.x, halfpixel.y) * offset;\n"
          + "    vec2 offset3 = vec2(0.0, halfpixel.y * 2.0) * offset;\n"
          + "    vec2 offset4 = vec2(halfpixel.x, halfpixel.y) * offset;\n"
          + "    vec2 offset5 = vec2(halfpixel.x * 2.0, 0.0) * offset;\n"
          + "    vec2 offset6 = vec2(halfpixel.x, -halfpixel.y) * offset;\n"
          + "    vec2 offset7 = vec2(0.0, -halfpixel.y * 2.0) * offset;\n"
          + "    vec2 offset8 = vec2(-halfpixel.x, -halfpixel.y) * offset;\n"
          + "    vec4 sum = texture2D(inTexture, uv + offset1);\n"
          + "    sum.rgb *= sum.a;\n"
          + "    vec4 smpl1 = texture2D(inTexture, uv + offset2);\n"
          + "    smpl1.rgb *= smpl1.a;\n"
          + "    sum += smpl1 * 2.0;\n"
          + "    vec4 smp2 = texture2D(inTexture, uv + offset3);\n"
          + "    smp2.rgb *= smp2.a;\n"
          + "    sum += smp2;\n"
          + "    vec4 smp3 = texture2D(inTexture, uv + offset4);\n"
          + "    smp3.rgb *= smp3.a;\n"
          + "    sum += smp3 * 2.0;\n"
          + "    vec4 smp4 = texture2D(inTexture, uv + offset5);\n"
          + "    smp4.rgb *= smp4.a;\n"
          + "    sum += smp4;\n"
          + "    vec4 smp5 = texture2D(inTexture, uv + offset6);\n"
          + "    smp5.rgb *= smp5.a;\n"
          + "    sum += smp5 * 2.0;\n"
          + "    vec4 smp6 = texture2D(inTexture, uv + offset7);\n"
          + "    smp6.rgb *= smp6.a;\n"
          + "    sum += smp6;\n"
          + "    vec4 smp7 = texture2D(inTexture, uv + offset8);\n"
          + "    smp7.rgb *= smp7.a;\n"
          + "    sum += smp7 * 2.0;\n"
          + "    vec4 result = sum / 12.0;\n"
          + "    float checkAlpha = texture2D(textureToCheck, gl_TexCoord[0].st).a;\n"
          + "    gl_FragColor = vec4(result.rgb / result.a, mix(result.a, result.a * (1.0 - checkAlpha), float(check)));\n"
          + "}";

  private static final String KAWASE_DOWN_BLOOM =
      "#version 120\n"
          + "uniform sampler2D inTexture;\n"
          + "uniform vec2 offset, halfpixel, iResolution;\n"
          + "void main() {\n"
          + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
          + "    vec4 sum = texture2D(inTexture, uv);\n"
          + "    sum.rgb *= sum.a;\n"
          + "    sum *= 4.0;\n"
          + "    vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);\n"
          + "    smp1.rgb *= smp1.a;\n"
          + "    sum += smp1;\n"
          + "    vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);\n"
          + "    smp2.rgb *= smp2.a;\n"
          + "    sum += smp2;\n"
          + "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n"
          + "    smp3.rgb *= smp3.a;\n"
          + "    sum += smp3;\n"
          + "    vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n"
          + "    smp4.rgb *= smp4.a;\n"
          + "    sum += smp4;\n"
          + "    vec4 result = sum / 8.0;\n"
          + "    gl_FragColor = vec4(result.rgb / result.a, result.a);\n"
          + "}";

  private static final String KAWASE_UP =
      "#version 120\n"
          + "uniform sampler2D inTexture, textureToCheck;\n"
          + "uniform vec2 halfpixel, offset, iResolution;\n"
          + "uniform int check;\n"
          + "void main() {\n"
          + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
          + "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n"
          + "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;\n"
          + "    sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n"
          + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;\n"
          + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n"
          + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;\n"
          + "    sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n"
          + "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;\n"
          + "    vec4 average = sum / 12.0;\n"
          + "    gl_FragColor = vec4(average.rgb, mix(1.0, texture2D(textureToCheck, gl_TexCoord[0].st).a, check));\n"
          + "}";

  private static final String KAWASE_DOWN =
      "#version 120\n"
          + "uniform sampler2D inTexture;\n"
          + "uniform vec2 offset, halfpixel, iResolution;\n"
          + "void main() {\n"
          + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
          + "    vec4 sum = texture2D(inTexture, uv) * 4.0;\n"
          + "    sum += texture2D(inTexture, uv - halfpixel.xy * offset);\n"
          + "    sum += texture2D(inTexture, uv + halfpixel.xy * offset);\n"
          + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n"
          + "    sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n"
          + "    gl_FragColor = vec4(sum.rgb * 0.125, 1.0);\n"
          + "}";

  private static final String ROUNDED_RECT =
      "#version 120\n"
          + "\n"
          + "uniform vec2 location, rectSize;\n"
          + "uniform vec4 color;\n"
          + "uniform float radius;\n"
          + "uniform bool blur;\n"
          + "\n"
          + "float roundSDF(vec2 p, vec2 b, float r) {\n"
          + "    return length(max(abs(p) - b, 0.0)) - r;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "    vec2 rectHalf = rectSize * .5;\n"
          + "    float smoothedAlpha = (1.0-smoothstep(0.0, 1.0, roundSDF(rectHalf - (gl_TexCoord[0].st * rectSize), rectHalf - radius - 1., radius))) * color.a;\n"
          + "    gl_FragColor = vec4(color.rgb, smoothedAlpha);\n"
          + "}";

  private static final String ROUND_RECT_OUTLINE =
      "#version 120\n"
          + "\n"
          + "uniform vec2 location, rectSize;\n"
          + "uniform vec4 color, outlineColor;\n"
          + "uniform float radius, outlineThickness;\n"
          + "\n"
          + "float roundedSDF(vec2 centerPos, vec2 size, float radius) {\n"
          + "    return length(max(abs(centerPos) - size + radius, 0.0)) - radius;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "    float distance = roundedSDF(gl_FragCoord.xy - location - (rectSize * .5), (rectSize * .5) + (outlineThickness *.5) - 1.0, radius);\n"
          + "    float blendAmount = smoothstep(0., 2., abs(distance) - (outlineThickness * .5));\n"
          + "    vec4 insideColor = (distance < 0.) ? color : vec4(outlineColor.rgb, 0.0);\n"
          + "    gl_FragColor = mix(outlineColor, insideColor, blendAmount);\n"
          + "}";

  private static final String ROUND_RECT_TEXTURE =
      "#version 120\n"
          + "\n"
          + "uniform vec2 location, rectSize;\n"
          + "uniform sampler2D textureIn;\n"
          + "uniform float radius, alpha;\n"
          + "\n"
          + "float roundedBoxSDF(vec2 centerPos, vec2 size, float radius) {\n"
          + "    return length(max(abs(centerPos) -size, 0.)) - radius;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "    float distance = roundedBoxSDF((rectSize * .5) - (gl_TexCoord[0].st * rectSize), (rectSize * .5) - radius - 1., radius);\n"
          + "    float smoothedAlpha = (1.0-smoothstep(0.0, 2.0, distance)) * alpha;\n"
          + "    gl_FragColor = vec4(texture2D(textureIn, gl_TexCoord[0].st).rgb, smoothedAlpha);\n"
          + "}";

  private static final String ROUNDED_RECT_GRADIENT =
      "#version 120\n"
          + "\n"
          + "uniform vec2 location, rectSize;\n"
          + "uniform vec4 color1, color2, color3, color4;\n"
          + "uniform float radius;\n"
          + "\n"
          + "#define NOISE .5/255.0\n"
          + "\n"
          + "float roundSDF(vec2 p, vec2 b, float r) {\n"
          + "    return length(max(abs(p) - b , 0.0)) - r;\n"
          + "}\n"
          + "\n"
          + "vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){\n"
          + "    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);\n"
          + "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));\n"
          + "    return color;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "    vec2 st = gl_TexCoord[0].st;\n"
          + "    vec2 halfSize = rectSize * .5;\n"
          + "    float smoothedAlpha = (1.0-smoothstep(0.0, 2., roundSDF(halfSize - (gl_TexCoord[0].st * rectSize), halfSize - radius - 1., radius)));\n"
          + "    vec4 gradient = createGradient(st, color1, color2, color3, color4);\n"
          + "    gl_FragColor = vec4(gradient.rgb, gradient.a * smoothedAlpha);\n"
          + "}";

  private static final String GRADIENT_MASK =
      "#version 120\n"
          + "\n"
          + "uniform vec2 location, rectSize;\n"
          + "uniform sampler2D tex;\n"
          + "uniform vec3 color1, color2, color3, color4;\n"
          + "uniform float alpha;\n"
          + "\n"
          + "#define NOISE .5/255.0\n"
          + "\n"
          + "vec3 createGradient(vec2 coords, vec3 color1, vec3 color2, vec3 color3, vec3 color4){\n"
          + "    vec3 color = mix(mix(color1.rgb, color2.rgb, coords.y), mix(color3.rgb, color4.rgb, coords.y), coords.x);\n"
          + "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898,78.233))) * 43758.5453));\n"
          + "    return color;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n"
          + "    float texColorAlpha = texture2D(tex, gl_TexCoord[0].st).a;\n"
          + "    gl_FragColor = vec4(createGradient(coords, color1, color2, color3, color4), texColorAlpha * alpha);\n"
          + "}";

  private static final String MASK =
      "#version 120\n"
          + "\n"
          + "uniform vec2 location, rectSize;\n"
          + "uniform sampler2D u_texture, u_texture2;\n"
          + "void main() {\n"
          + "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n"
          + "    float texColorAlpha = texture2D(u_texture, gl_TexCoord[0].st).a;\n"
          + "    vec3 tex2Color = texture2D(u_texture2, gl_TexCoord[0].st).rgb;\n"
          + "    gl_FragColor = vec4(tex2Color, texColorAlpha);\n"
          + "}";

  private static final String GRADIENT =
      "#version 120\n"
          + "\n"
          + "uniform vec2 location, rectSize;\n"
          + "uniform sampler2D tex;\n"
          + "uniform vec4 color1, color2, color3, color4;\n"
          + "#define NOISE .5/255.0\n"
          + "\n"
          + "vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){\n"
          + "    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);\n"
          + "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));\n"
          + "    return color;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n"
          + "    gl_FragColor = createGradient(coords, color1, color2, color3, color4);\n"
          + "}";

  private static final String ROUNDED_RECT_RISE =
      "#version 120\n"
          + "\n"
          + "uniform vec2 u_size;\n"
          + "uniform float u_radius;\n"
          + "uniform vec4 u_color;\n"
          + "uniform vec4 u_edges;\n"
          + "\n"
          + "void main(void)\n"
          + "{\n"
          + "    vec2 tex_coord = gl_TexCoord[0].st;\n"
          + "    if (tex_coord.x < 0.5 && tex_coord.y > 0.5 && u_edges.x == 0.0 ||\n"
          + "        tex_coord.x > 0.5 && tex_coord.y > 0.5 && u_edges.y == 0.0 ||\n"
          + "        tex_coord.x > 0.5 && tex_coord.y < 0.5 && u_edges.z == 0.0 ||\n"
          + "        tex_coord.x < 0.5 && tex_coord.y < 0.5 && u_edges.w == 0.0) {\n"
          + "        gl_FragColor = u_color;\n"
          + "    } else {\n"
          + "        gl_FragColor = vec4(u_color.rgb, u_color.a * smoothstep(1.0, 0.0, length(max((abs(tex_coord - 0.5) + 0.5) * u_size - u_size + u_radius, 0.0)) - u_radius + 0.5));\n"
          + "    }\n"
          + "}";

  public ShaderUtils(String fragmentShaderLoc) {
    this(fragmentShaderLoc, "minecraft:shaders/vertex.vsh");
  }

  public ShaderUtils(String fragmentShaderLoc, String vertexShaderLoc) {
    int program = glCreateProgram();
    try {
      int fragmentShaderID;
      switch (fragmentShaderLoc) {
        case "kawaseUpGlow":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(KAWASE_UP_GLOW.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "glow":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(GLOW_SHADER.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "chams":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(CHAMS.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "kawaseUpBloom":
          fragmentShaderID =
              createShader(
                  new ByteArrayInputStream(KAWASE_UP_BLOOM.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "kawaseDownBloom":
          fragmentShaderID =
              createShader(
                  new ByteArrayInputStream(KAWASE_DOWN_BLOOM.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "kawaseUp":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(KAWASE_UP.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "kawaseDown":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(KAWASE_DOWN.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "roundedRect":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(ROUNDED_RECT.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "roundRectOutline":
          fragmentShaderID =
              createShader(
                  new ByteArrayInputStream(ROUND_RECT_OUTLINE.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "roundRectTexture":
          fragmentShaderID =
              createShader(
                  new ByteArrayInputStream(ROUND_RECT_TEXTURE.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "roundedRectGradient":
          fragmentShaderID =
              createShader(
                  new ByteArrayInputStream(ROUNDED_RECT_GRADIENT.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "gradientMask":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(GRADIENT_MASK.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "mask":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(MASK.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "gradient":
          fragmentShaderID =
              createShader(new ByteArrayInputStream(GRADIENT.getBytes()), GL_FRAGMENT_SHADER);
          break;
        case "roundedRectRise":
          fragmentShaderID =
              createShader(
                  new ByteArrayInputStream(ROUNDED_RECT_RISE.getBytes()), GL_FRAGMENT_SHADER);
          break;
        default:
          fragmentShaderID =
              createShader(
                  mc.getResourceManager()
                      .getResource(new ResourceLocation(fragmentShaderLoc))
                      .getInputStream(),
                  GL_FRAGMENT_SHADER);
          break;
      }
      glAttachShader(program, fragmentShaderID);

      int vertexShaderID =
          createShader(
              mc.getResourceManager()
                  .getResource(new ResourceLocation(vertexShaderLoc))
                  .getInputStream(),
              GL_VERTEX_SHADER);
      glAttachShader(program, vertexShaderID);
    } catch (IOException e) {
      e.printStackTrace();
    }

    glLinkProgram(program);
    int status = glGetProgrami(program, GL_LINK_STATUS);
    if (status == 0) {
      throw new IllegalStateException("Shader failed to link!");
    }
    this.programID = program;
  }

  public static void drawQuads() {
    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
    float width = (float) sr.getScaledWidth_double();
    float height = (float) sr.getScaledHeight_double();
    glBegin(GL_QUADS);
    glTexCoord2f(0, 1);
    glVertex2f(0, 0);
    glTexCoord2f(0, 0);
    glVertex2f(0, height);
    glTexCoord2f(1, 0);
    glVertex2f(width, height);
    glTexCoord2f(1, 1);
    glVertex2f(width, 0);
    glEnd();
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

  public void init() {
    OpenGlHelper.glUseProgram(programID);
  }

  public void unload() {
    OpenGlHelper.glUseProgram(0);
  }

  public void setUniformf(String name, float... args) {
    int loc = getUniformLocation(name);
    if (loc == -1) return;
    switch (args.length) {
      case 1:
        glUniform1f(loc, args[0]);
        break;
      case 2:
        glUniform2f(loc, args[0], args[1]);
        break;
      case 3:
        glUniform3f(loc, args[0], args[1], args[2]);
        break;
      case 4:
        glUniform4f(loc, args[0], args[1], args[2], args[3]);
        break;
    }
  }

  public void setUniformi(String name, int... args) {
    int loc = getUniformLocation(name);
    if (loc == -1) return;
    if (args.length > 1) glUniform2i(loc, args[0], args[1]);
    else glUniform1i(loc, args[0]);
  }

  public int getUniform(String name) {
    return getUniformLocation(name);
  }

  private int getUniformLocation(String name) {
    Integer cached = uniformLocations.get(name);
    if (cached != null) return cached;
    int location = glGetUniformLocation(programID, name);
    uniformLocations.put(name, location);
    return location;
  }

  private int createShader(InputStream inputStream, int shaderType) {
    int shader = glCreateShader(shaderType);
    try {
      String source = readInputStream(inputStream);
      GL20.glShaderSource(shader, source);
    } finally {
      try {
        inputStream.close();
      } catch (IOException ignored) {
      }
    }
    glCompileShader(shader);
    if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
      System.out.println(glGetShaderInfoLog(shader, 4096));
      throw new IllegalStateException(String.format("Shader (%s) failed to compile!", shaderType));
    }
    return shader;
  }

  private static String readInputStream(InputStream inputStream) {
    try {
      java.util.Scanner scanner = new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A");
      return scanner.hasNext() ? scanner.next() : "";
    } catch (Exception e) {
      return "";
    }
  }
}
