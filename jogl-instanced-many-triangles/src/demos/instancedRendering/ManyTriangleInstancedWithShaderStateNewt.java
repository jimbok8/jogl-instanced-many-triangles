/**
 * Copyright 2014 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */

package demos.instancedRendering;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.media.opengl.DebugGL4;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2ES2;
import javax.media.opengl.GL4;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLException;
import javax.media.opengl.GLProfile;
import javax.media.opengl.GLUniformData;
import javax.media.opengl.TraceGL4;
import javax.swing.SwingUtilities;

import com.jogamp.newt.event.MouseAdapter;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.math.Matrix4;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.GLArrayDataClient;
import com.jogamp.opengl.util.GLArrayDataServer;
import com.jogamp.opengl.util.PMVMatrix;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import com.jogamp.opengl.util.glsl.ShaderState;

public class ManyTriangleInstancedWithShaderStateNewt implements GLEventListener {
	private final GLWindow glWindow;
	private final FPSAnimator animator;
	private float aspect;

	private static final int WINDOW_WIDTH = 800;
	private static final int WINDOW_HEIGHT = 600;

	protected float winScale = 0.1f;
	private static final float WIN_SCALE_MIN = 1e-3f;
	private static final float WIN_SCALE_MAX = 100f;

	private static final int NO_OF_INSTANCE = 30;

	private static final String shaderBasename = "triangles";
//	private static final boolean useInterleaved = false;
	private static final boolean useInterleaved = true;

	private ShaderState st;
	private GLArrayDataServer interleavedVBO;
	private GLArrayDataClient verticesVBO;
	private GLArrayDataClient colorsVBO;

	private PMVMatrix projectionMatrix;
	private GLUniformData projectionMatrixUniform;
	private GLUniformData transformMatrixUniform;
	private final FloatBuffer triangleTransform = FloatBuffer.allocate(16 * NO_OF_INSTANCE);

	private final Matrix4[] mat = new Matrix4[NO_OF_INSTANCE];
	private final float[] rotationSpeed = new float[NO_OF_INSTANCE];

	private static final boolean useTraceGL = false;
	private PrintStream stream;

	private boolean isInitialized = false;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new ManyTriangleInstancedWithShaderStateNewt();
			}
		});
	}

	public ManyTriangleInstancedWithShaderStateNewt() {
		if(useTraceGL) {
			try {
				stream = new PrintStream(new FileOutputStream(new File("instanced-with-st.txt")));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		initTransform();

		GLProfile prof = GLProfile.get(GLProfile.GL4);
		GLCapabilities caps = new GLCapabilities(prof);
		glWindow = GLWindow.create(caps);

		glWindow.addGLEventListener(this);

		glWindow.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseWheelMoved(MouseEvent e) {
				float[] step = e.getRotation();
				if(step[1] > 0) {
					winScale *= 1.05;
					if(winScale > WIN_SCALE_MAX) winScale = WIN_SCALE_MAX;
				} else if(0 > step[1] ) {
					winScale *= 0.95;
					if(winScale < WIN_SCALE_MIN) winScale = WIN_SCALE_MIN;
				}
				System.out.println("step:" + step[0] + "," + step[1] + "," + step[2] + ", scale:" + winScale);
			}
		});

		glWindow.addWindowListener(new WindowAdapter() {

			@Override
			public void windowDestroyed(WindowEvent evt) {
				System.out.println("Closed");
				if(animator.isAnimating()) animator.stop();
			}

			@Override
			public void windowDestroyNotify(WindowEvent evt) {
				System.out.println("destroy notified.");
				animator.stop();
			}
		});

    	animator = new FPSAnimator(glWindow, 60, true);
    	animator.start();

    	glWindow.setTitle("Instanced rendering experiment");
        glWindow.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        glWindow.setUndecorated(false);
        glWindow.setPointerVisible(true);
        glWindow.setVisible(true);
	}

	private void initTransform() {
		Random rnd = new Random();
		for(int i = 0; i < NO_OF_INSTANCE; i++) {
			rotationSpeed[i] = 0.3f * rnd.nextFloat();
			mat[i] = new Matrix4();
			mat[i].loadIdentity();
			float scale = 1f + 4 * rnd.nextFloat();
			mat[i].scale(scale, scale, scale);
			//setup initial position of each triangle
			mat[i].translate(20f * rnd.nextFloat() - 10f,
							 10f * rnd.nextFloat() -  5f,
							 0f);
		}
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		GL4 gl = drawable.getGL().getGL4();
		drawable.setGL(new DebugGL4(gl));
		if(useTraceGL) {
			drawable.setGL(new TraceGL4(gl, stream));
		}

		gl.glClearColor(1, 1, 1, 1); //white background
//		gl.glClearColor(0, 0, 0, 1); //black background
		gl.glClearDepth(1.0f);

		System.err.println("Chosen GLCapabilities: " + drawable.getChosenGLCapabilities());
		System.err.println("INIT GL IS: " + gl.getClass().getName());
		System.err.println("GL_VENDOR: " + gl.glGetString(GL4.GL_VENDOR));
		System.err.println("GL_RENDERER: " + gl.glGetString(GL4.GL_RENDERER));
		System.err.println("GL_VERSION: " + gl.glGetString(GL4.GL_VERSION));

		initShader(gl);
        projectionMatrix = new PMVMatrix();
		projectionMatrixUniform = new GLUniformData("mgl_PMatrix", 4, 4, projectionMatrix.glGetPMatrixf());
		st.ownUniform(projectionMatrixUniform);
        if(!st.uniform(gl, projectionMatrixUniform)) {
            throw new GLException("Error setting mgl_PMatrix in shader: " + st);
        }

        transformMatrixUniform =  new GLUniformData("mgl_MVMatrix", 4, 4, triangleTransform);

        st.ownUniform(transformMatrixUniform);
        if(!st.uniform(gl, transformMatrixUniform)) {
            throw new GLException("Error setting mgl_MVMatrix in shader: " + st);
        }

        if(useInterleaved) {
        	initVBO_interleaved(gl);
        } else {
        	initVBO_nonInterleaved(gl);
        }

		isInitialized = true;
	}

	private void initVBO_nonInterleaved(GL4 gl) {
		int VERTEX_COUNT = 3;

        verticesVBO = GLArrayDataClient.createGLSL("mgl_Vertex", 3, GL4.GL_FLOAT, false, VERTEX_COUNT);
        FloatBuffer verticeBuf = (FloatBuffer)verticesVBO.getBuffer();
        verticeBuf.put(vertices);
        verticesVBO.seal(gl, true);

        colorsVBO = GLArrayDataClient.createGLSL("mgl_Color",  4, GL4.GL_FLOAT, false, VERTEX_COUNT);
        FloatBuffer colorBuf = (FloatBuffer)colorsVBO.getBuffer();
        colorBuf.put(colors);
        colorsVBO.seal(gl, true);

        verticesVBO.enableBuffer(gl, false);
        colorsVBO.enableBuffer(gl, false);

        st.ownAttribute(verticesVBO, true);
        st.ownAttribute(colorsVBO, true);
		st.useProgram(gl, false);
	}

	private void initVBO_interleaved(GL4 gl) {
		int VERTEX_COUNT = 3;
		interleavedVBO = GLArrayDataServer.createGLSLInterleaved(3 + 4, GL.GL_FLOAT, false, VERTEX_COUNT, GL.GL_STATIC_DRAW);
        interleavedVBO.addGLSLSubArray("mgl_Vertex", 3, GL.GL_ARRAY_BUFFER);
        interleavedVBO.addGLSLSubArray("mgl_Color",  4, GL.GL_ARRAY_BUFFER);

        FloatBuffer ib = (FloatBuffer)interleavedVBO.getBuffer();

        for(int i = 0; i < VERTEX_COUNT; i++) {
            ib.put(vertices,  i*3, 3);
            ib.put(colors,    i*4, 4);
        }
        interleavedVBO.seal(gl, true);
        interleavedVBO.enableBuffer(gl, false);
        st.ownAttribute(interleavedVBO, true);
		st.useProgram(gl, false);
	}

    private void initShader(GL4 gl) {
        // Create & Compile the shader objects
        ShaderCode vp0 = ShaderCode.create(gl, GL2ES2.GL_VERTEX_SHADER, this.getClass(),
                                            "shader", "shader/bin", shaderBasename, true);
        ShaderCode fp0 = ShaderCode.create(gl, GL2ES2.GL_FRAGMENT_SHADER, this.getClass(),
                                            "shader", "shader/bin", shaderBasename, true);
        vp0.replaceInShaderSource("NO_OF_INSTANCE", String.valueOf(NO_OF_INSTANCE));

        vp0.defaultShaderCustomization(gl, true, true);
        fp0.defaultShaderCustomization(gl, true, true);

        //vp0.dumpShaderSource(System.out);

        // Create & Link the shader program
        ShaderProgram sp = new ShaderProgram();
        sp.add(vp0);
        sp.add(fp0);
        if(!sp.link(gl, System.err)) {
            throw new GLException("Couldn't link program: "+sp);
        }

        // Let's manage all our states using ShaderState.
        st = new ShaderState();
        st.attachShaderProgram(gl, sp, true);
    }

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL4 gl3 = drawable.getGL().getGL4();
		gl3.glViewport(0, 0, width, height);
		aspect = (float) width / (float) height;

		projectionMatrix.glMatrixMode(GL2.GL_PROJECTION);
		projectionMatrix.glLoadIdentity();
		projectionMatrix.gluPerspective(45, aspect, 0.001f, 20f);
		projectionMatrix.gluLookAt(0, 0, -10, 0, 0, 0, 0, 1, 0);
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		if(!isInitialized ) return;

		GL4 gl = drawable.getGL().getGL4();
		gl.glClear(GL4.GL_COLOR_BUFFER_BIT | GL4.GL_DEPTH_BUFFER_BIT);

		st.useProgram(gl, true);
		projectionMatrix.glMatrixMode(GL2.GL_PROJECTION);
		projectionMatrix.glPushMatrix();
		projectionMatrix.glScalef(winScale, winScale, winScale);
		projectionMatrix.update();
		st.uniform(gl, projectionMatrixUniform);
		projectionMatrix.glPopMatrix();

		generateTriangleTransform();
		st.uniform(gl, transformMatrixUniform);
		if(useInterleaved) {
			interleavedVBO.enableBuffer(gl, true);
		} else {
			verticesVBO.enableBuffer(gl, true);
			colorsVBO.enableBuffer(gl, true);
		}
		//gl.glVertexAttribDivisor() is not required since each instance has the same attribute (color).
		gl.glDrawArraysInstanced(GL4.GL_TRIANGLES, 0, 3, NO_OF_INSTANCE);
		if(useInterleaved) {
			interleavedVBO.enableBuffer(gl, false);
		} else {
			verticesVBO.enableBuffer(gl, false);
			colorsVBO.enableBuffer(gl, false);
		}
		st.useProgram(gl, false);
	}

	private void generateTriangleTransform() {
		triangleTransform.clear();
		for(int i = 0; i < NO_OF_INSTANCE; i++) {
			mat[i].rotate(rotationSpeed[i], 0, 0, 1);
			triangleTransform.put(mat[i].getMatrix());
		}
		triangleTransform.rewind();
	}

	@Override
	public void dispose(GLAutoDrawable drawable){
		GL4 gl = drawable.getGL().getGL4();
		st.destroy(gl);
	}

	private static final float[] vertices = {
			1.0f, 0.0f, 0,
			-0.5f, 0.866f, 0,
			-0.5f, -0.866f, 0
	};

	private final float[] colors = {
			1.0f, 0.0f, 0.0f, 1.0f,
			0.0f, 1.0f, 0.0f, 1.0f,
			0f, 0f, 1.0f, 1f
	};
}
