package demos.triangles;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.media.opengl.DebugGL4;
import javax.media.opengl.GL2;
import javax.media.opengl.GL4;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.math.Matrix4;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.PMVMatrix;

public class ManyTriangleVAOInstancedExperiment0 implements GLEventListener {
	private final JFrame frame;
	private final FPSAnimator animator;
	private final GLCanvas panel;
	private final Dimension dim = new Dimension(1024, 768);
	private float aspect;

	protected float winScale = 0.1f;
	private static final float SCALE_MIN = 1e-10f;
	private static final float SCALE_MAX = 10000f;

	private int shaderProgram;
	private int vertShader;
	private int fragShader;
	private int projectionMatrixLocation;
	private int transformMatrixLocation;
	private static final int locPos = 1;
	private static final int locCol = 2;

	private static final int NO_OF_TRIANGLES = 30;

	private PMVMatrix projectionMatrix;

	private int[] vbo;
	private int[] vao;
	private final Matrix4[] mat = new Matrix4[NO_OF_TRIANGLES];
	private final float[] rotationSpeed = new float[NO_OF_TRIANGLES];

	public static void main(String[] args){
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new ManyTriangleVAOInstancedExperiment0();
			}
		});
	}

	public ManyTriangleVAOInstancedExperiment0() {
		initTransform();

		frame = new JFrame(this.getClass().getSimpleName());
		frame.setLayout(new BorderLayout());
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				animator.stop();
				System.exit(0);
			}
		});
		panel = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL2GL3)));
		panel.addGLEventListener(this);

		frame.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				int step = e.getWheelRotation();
				if(step > 0) {
					winScale *= 1.05;
					if(winScale > SCALE_MAX) winScale = SCALE_MAX;
				} else if(0 > step ) {
					winScale *= 0.95;
					if(winScale < SCALE_MIN) winScale = SCALE_MIN;
				}
			}
		});

		panel.setPreferredSize(dim);
		frame.add(panel, BorderLayout.CENTER);
		frame.pack();
		frame.setVisible(true);
		animator = new FPSAnimator(panel, 60, true);
		animator.start();
	}

	private void initTransform() {
		Random rnd = new Random();
		for(int i = 0; i < NO_OF_TRIANGLES; i++) {
			rotationSpeed[i] = 0.3f * rnd.nextFloat();
			mat[i] = new Matrix4();
			mat[i].loadIdentity();
			float scale = 1f + 4 * rnd.nextFloat();
			mat[i].scale(scale, scale, scale);
			mat[i].rotate(0, 0, 0, 1);
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

		gl.glClearColor(1, 1, 1, 1);
//		gl.glClearColor(0, 0, 0, 1);
		gl.glClearDepth(1.0f);

		System.err.println("Chosen GLCapabilities: " + drawable.getChosenGLCapabilities());
		System.err.println("INIT GL IS: " + gl.getClass().getName());
		System.err.println("GL_VENDOR: " + gl.glGetString(GL4.GL_VENDOR));
		System.err.println("GL_RENDERER: " + gl.glGetString(GL4.GL_RENDERER));
		System.err.println("GL_VERSION: " + gl.glGetString(GL4.GL_VERSION));

		initShaders(gl);
		initVBO(gl);
	}

	private void initVBO(GL4 gl) {
		FloatBuffer interleavedBuffer = Buffers.newDirectFloatBuffer(vertices.length + colors.length);
		for(int i = 0; i < vertices.length/3; i++) {
			for(int j = 0; j < 3; j++) {
				interleavedBuffer.put(vertices[i*3 + j]);
			}
			for(int j = 0; j < 4; j++) {
				interleavedBuffer.put(colors[i*4 + j]);
			}
		}
		interleavedBuffer.flip();

		vao = new int[1];
		gl.glGenVertexArrays(1, vao , 0);
		gl.glBindVertexArray(vao[0]);
		vbo = new int[1];
		gl.glGenBuffers(1, vbo, 0);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vbo[0]);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, interleavedBuffer.limit() * Buffers.SIZEOF_FLOAT, interleavedBuffer, GL4.GL_STATIC_DRAW);

		gl.glEnableVertexAttribArray(locPos);
		gl.glEnableVertexAttribArray(locCol);

		int stride = Buffers.SIZEOF_FLOAT * (3+4);
		gl.glVertexAttribPointer( locPos, 3, GL4.GL_FLOAT, false, stride, 0);
		gl.glVertexAttribPointer( locCol, 4, GL4.GL_FLOAT, false, stride, Buffers.SIZEOF_FLOAT * 3);
	}

	private void initShaders(GL4 gl) {
		vertShader = gl.glCreateShader(GL4.GL_VERTEX_SHADER);
		fragShader = gl.glCreateShader(GL4.GL_FRAGMENT_SHADER);

		String[] vlines = new String[] { vertexShaderString };
		int[] vlengths = new int[] { vlines[0].length() };
		gl.glShaderSource(vertShader, vlines.length, vlines, vlengths, 0);
		gl.glCompileShader(vertShader);

		int[] compiled = new int[1];
		gl.glGetShaderiv(vertShader, GL4.GL_COMPILE_STATUS, compiled, 0);
		if(compiled[0] != 0) {
			System.out.println("Vertex shader compiled");
		} else {
			int[] logLength = new int[1];
			gl.glGetShaderiv(vertShader, GL4.GL_INFO_LOG_LENGTH, logLength, 0);

			byte[] log = new byte[logLength[0]];
			gl.glGetShaderInfoLog(vertShader, logLength[0], (int[])null, 0, log, 0);

			System.err.println("Error compiling the vertex shader: " + new String(log));
			System.exit(1);
		}

		String[] flines = new String[] { fragmentShaderString };
		int[] flengths = new int[] { flines[0].length() };
		gl.glShaderSource(fragShader, flines.length, flines, flengths, 0);
		gl.glCompileShader(fragShader);

		gl.glGetShaderiv(fragShader, GL4.GL_COMPILE_STATUS, compiled, 0);
		if(compiled[0] != 0){
			System.out.println("Fragment shader compiled.");
		} else {
			int[] logLength = new int[1];
			gl.glGetShaderiv(fragShader, GL4.GL_INFO_LOG_LENGTH, logLength, 0);

			byte[] log = new byte[logLength[0]];
			gl.glGetShaderInfoLog(fragShader, logLength[0], (int[])null, 0, log, 0);

			System.err.println("Error compiling the fragment shader: " + new String(log));
			System.exit(1);
		}

		shaderProgram = gl.glCreateProgram();
		gl.glAttachShader(shaderProgram, vertShader);
		gl.glAttachShader(shaderProgram, fragShader);

		gl.glBindAttribLocation(shaderProgram, locPos, "VertexPosition");
		gl.glBindAttribLocation(shaderProgram, locCol, "VertexColor");

		gl.glLinkProgram(shaderProgram);

		projectionMatrixLocation = gl.glGetUniformLocation(shaderProgram, "uniform_Projection");
		System.out.println("projectionMatrixLocation:" + projectionMatrixLocation);
		transformMatrixLocation = gl.glGetUniformLocation(shaderProgram, "uniform_Transform");
		System.out.println("transformMatrixLocation:" + transformMatrixLocation);
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		System.out.println("Window resized to width=" + width + " height=" + height);
		GL4 gl3 = drawable.getGL().getGL4();
		gl3.glViewport(0, 0, width, height);
		aspect = (float) width / (float) height;

		projectionMatrix = new PMVMatrix();
		projectionMatrix.glMatrixMode(GL2.GL_PROJECTION);
		projectionMatrix.glLoadIdentity();
		projectionMatrix.gluPerspective(45, aspect, 0.001f, 20f);
		projectionMatrix.gluLookAt(0, 0, -10, 0, 0, 0, 0, 1, 0);
	}

	@Override
	public void display(GLAutoDrawable drawable) {

		GL4 gl = drawable.getGL().getGL4();
		gl.glClear(GL4.GL_COLOR_BUFFER_BIT | GL4.GL_DEPTH_BUFFER_BIT);

		gl.glUseProgram(shaderProgram);

		projectionMatrix.glMatrixMode(GL2.GL_PROJECTION);

		projectionMatrix.glPushMatrix();
		projectionMatrix.glScalef(winScale, winScale, winScale);
		projectionMatrix.update();
		gl.glUniformMatrix4fv(projectionMatrixLocation, 1, false, projectionMatrix.glGetPMatrixf());
		projectionMatrix.glPopMatrix();
		generateTriangleTransform();
		gl.glUniformMatrix4fv(transformMatrixLocation, NO_OF_TRIANGLES, false, triangleTransform);

		gl.glBindVertexArray(vao[0]);
		gl.glDrawArraysInstanced(GL4.GL_TRIANGLES, 0, 3, NO_OF_TRIANGLES);
		gl.glBindVertexArray(0);
		gl.glUseProgram(0);
	}

	FloatBuffer triangleTransform = FloatBuffer.allocate(16 * NO_OF_TRIANGLES);

	private void generateTriangleTransform() {
		triangleTransform.clear();
		for(int i = 0; i < NO_OF_TRIANGLES; i++) {
			mat[i].translate(0.1f, 0.1f, 0);
			mat[i].rotate(rotationSpeed[i], 0, 0, 1);
			mat[i].translate(-0.1f, -0.1f, 0);
			triangleTransform.put(mat[i].getMatrix());
		}
		triangleTransform.flip();
	}

	@Override
	public void dispose(GLAutoDrawable drawable){
		GL4 gl = drawable.getGL().getGL4();
		gl.glUseProgram(0);
		gl.glDeleteBuffers(2, vbo, 0);
		gl.glDetachShader(shaderProgram, vertShader);
		gl.glDeleteShader(vertShader);
		gl.glDetachShader(shaderProgram, fragShader);
		gl.glDeleteShader(fragShader);
		gl.glDeleteProgram(shaderProgram);
	}

	private final String vertexShaderString =
			"#version 330 \n" +
					"\n" +
					"uniform mat4 uniform_Projection; \n" +
					"uniform mat4 uniform_Transform[" + NO_OF_TRIANGLES + "]; \n" +
					"in vec4  VertexPosition; \n" +
					"in vec4  VertexColor; \n" +
					"out vec4    varying_Color; \n" +
					"void main(void) \n" +
					"{ \n" +
					"  varying_Color = VertexColor; \n" +
					//"  gl_Position = uniform_Projection * VertexPosition; \n" +
//					"  gl_Position = uniform_Transform[gl_InstanceID] * VertexPosition; \n" +
					"  gl_Position = uniform_Projection * uniform_Transform[gl_InstanceID] * VertexPosition; \n" +
					"} ";

	private final String fragmentShaderString =
			"#version 330\n" +
					"\n" +
					"in vec4    varying_Color; \n" +
					"out vec4    mgl_FragColor; \n" +
					"void main (void) \n" +
					"{ \n" +
					"  mgl_FragColor = varying_Color; \n" +
					"} ";

	private final float[] vertices = {
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
