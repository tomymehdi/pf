/*
 * Java port of Bullet (c) 2008 Martin Dvorak <jezek2@advel.cz>
 *
 * Bullet Continuous Collision Detection and Physics Library
 * Copyright (c) 2003-2008 Erwin Coumans  http://www.bulletphysics.com/
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from
 * the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose, 
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package ar.com.edu.it.itba.PF3;

import java.io.File;

import com.bulletphysics.BulletGlobals;
import com.bulletphysics.util.ObjectArrayList;
import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CapsuleShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.CylinderShape;

import ar.com.edu.it.itba.PF3.genericjoint.RagDoll.BodyPart;
import ar.com.edu.it.itba.PF3.genericjoint.RagDoll.JointType;
import ar.com.edu.it.itba.PF3.opengl.DemoApplication;
import ar.com.edu.it.itba.PF3.opengl.GLDebugDrawer;
import ar.com.edu.it.itba.PF3.opengl.IGL;
import ar.com.edu.it.itba.PF3.opengl.LWJGL;

import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;

import javax.vecmath.Vector3f;

import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;

import static ar.com.edu.it.itba.PF3.opengl.IGL.*;

/**
 * BasicDemo is good starting point for learning the code base and porting.
 * 
 * @author jezek2
 */
public class BasicDemo extends DemoApplication {

	// create 125 (5x5x5) dynamic object
	private static final int ARRAY_SIZE_X = 5;
	private static final int ARRAY_SIZE_Y = 5;
	private static final int ARRAY_SIZE_Z = 5;

	// maximum number of objects (and allow user to shoot additional boxes)
	private static final int MAX_PROXIES = (ARRAY_SIZE_X*ARRAY_SIZE_Y*ARRAY_SIZE_Z + 1024);

	private static final int START_POS_X = -5;
	private static final int START_POS_Y = -5;
	private static final int START_POS_Z = -3;
	
	// keep the collision shapes, for deletion/cleanup
	private ObjectArrayList<CollisionShape> collisionShapes = new ObjectArrayList<CollisionShape>();
	private BroadphaseInterface broadphase;
	private CollisionDispatcher dispatcher;
	private ConstraintSolver solver;
	private DefaultCollisionConfiguration collisionConfiguration;

	public BasicDemo(IGL gl) {
		super(gl);
	}
	
	@Override
	public void clientMoveAndDisplay() {
		gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		// simple dynamics world doesn't handle fixed-time-stepping
		float ms = getDeltaTimeMicroseconds();

		// step the simulation
		if (dynamicsWorld != null) {
			dynamicsWorld.stepSimulation(ms / 1000000f);
			// optional but useful: debug drawing
			dynamicsWorld.debugDrawWorld();
		}

		renderme();

		//glFlush();
		//glutSwapBuffers();
	}

	@Override
	public void displayCallback() {
		gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		renderme();

		// optional but useful: debug drawing to detect problems
		if (dynamicsWorld != null) {
			dynamicsWorld.debugDrawWorld();
		}

		//glFlush();
		//glutSwapBuffers();
	}

	@Override
	public void initPhysics() {
		setCameraDistance(30f);

		// collision configuration contains default setup for memory, collision setup
		collisionConfiguration = new DefaultCollisionConfiguration();

		// use the default collision dispatcher. For parallel processing you can use a diffent dispatcher (see Extras/BulletMultiThreaded)
		dispatcher = new CollisionDispatcher(collisionConfiguration);

		broadphase = new DbvtBroadphase();

		// the default constraint solver. For parallel processing you can use a different solver (see Extras/BulletMultiThreaded)
		SequentialImpulseConstraintSolver sol = new SequentialImpulseConstraintSolver();
		solver = sol;
		
		// TODO: needed for SimpleDynamicsWorld
		//sol.setSolverMode(sol.getSolverMode() & ~SolverMode.SOLVER_CACHE_FRIENDLY.getMask());
		
		dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);

		dynamicsWorld.setGravity(new Vector3f(0f, -10f, 0f));

		// create a few basic rigid bodies
		CollisionShape groundShape = new BoxShape(new Vector3f(50f, 1f, 50f));
		//CollisionShape groundShape = new StaticPlaneShape(new Vector3f(0, 1, 0), 50);

		collisionShapes.add(groundShape);

		Transform groundTransform = new Transform();
		groundTransform.setIdentity();
		groundTransform.origin.set(0, 0, 0);

		// We can also use DemoApplication::localCreateRigidBody, but for clarity it is provided here:
		{
			float mass = 0f;

			// rigidbody is dynamic if and only if mass is non zero, otherwise static
			boolean isDynamic = (mass != 0f);

			Vector3f localInertia = new Vector3f(0, 0, 0);
			if (isDynamic) {
				groundShape.calculateLocalInertia(mass, localInertia);
			}

			// using motionstate is recommended, it provides interpolation capabilities, and only synchronizes 'active' objects
			DefaultMotionState myMotionState = new DefaultMotionState(groundTransform);
			RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(mass, myMotionState, groundShape, localInertia);
			RigidBody body = new RigidBody(rbInfo);

			// add the body to the dynamics world
			dynamicsWorld.addRigidBody(body);
		}

		{
			// create a few dynamic rigidbodies
			// Re-using the same collision is better for memory usage and performance

			//CollisionShape colShape = new BoxShape(new Vector3f(1, 1, 1));
			//CollisionShape colShape = new SphereShape(1f);
			CollisionShape colShape = new CapsuleShape(3f, 9f);
			//CollisionShape colShape = new CylinderShape(new Vector3f(1,1,1));
			collisionShapes.add(colShape);

			// Create Dynamic Objects
			Transform startTransform = new Transform();
			startTransform.setIdentity();

			float mass = 1f;

			// rigidbody is dynamic if and only if mass is non zero, otherwise static
			boolean isDynamic = (mass != 0f);

			Vector3f localInertia = new Vector3f(0, 0, 0);
			if (isDynamic) {
				colShape.calculateLocalInertia(mass, localInertia);
			}

			startTransform.origin.set(0, 20, 0);

			// using motionstate is recommended, it provides interpolation capabilities, and only synchronizes 'active' objects
			DefaultMotionState myMotionState = new DefaultMotionState(startTransform);
			RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(mass, myMotionState, colShape, localInertia);
			RigidBody body = new RigidBody(rbInfo);
			body.setActivationState(CollisionObject.ISLAND_SLEEPING);

			dynamicsWorld.addRigidBody(body);
			body.setActivationState(CollisionObject.ISLAND_SLEEPING);
			
			CollisionShape colShape2 = new CapsuleShape(3f, 9f);
			collisionShapes.add(colShape2);
			
			Transform startTransform2 = new Transform();
			startTransform2.setIdentity();
			float mass2 = 1f;
			
			boolean isDynamic2 = (mass2 != 0f);
			
			Vector3f localIntertia2 = new Vector3f(0, 0, 0);
			if(isDynamic2) {
				colShape2.calculateLocalInertia(mass2, localIntertia2);
			}
		
			startTransform2.origin.set(5, 20, 0);
			
			DefaultMotionState myMotionState2 = new DefaultMotionState(startTransform2);
			RigidBodyConstructionInfo rbInfo2 = new RigidBodyConstructionInfo(mass2, myMotionState2, colShape2, localIntertia2);
			RigidBody body2 = new RigidBody(rbInfo2);
			body2.setActivationState(CollisionObject.ISLAND_SLEEPING);
			
			dynamicsWorld.addRigidBody(body2);
			body2.setActivationState(CollisionObject.ISLAND_SLEEPING);
			
			Transform localA = new Transform();
			Transform localB = new Transform();
			localA.setIdentity();
			localB.setIdentity();

			localA.origin.set(0f, 7.5f, 0f);
			localB.origin.set(0f, -7.5f, 0f);
			Generic6DofConstraint joint6DOF = new Generic6DofConstraint(
					body, 
					body2, 
					localA, 
					localB, 
					true);
			
			joint6DOF.setLimit(0, 0, 0);
			joint6DOF.setLimit(1, 0, 0);
			joint6DOF.setLimit(2, 0, 0);
			joint6DOF.setLimit(3, 0, 0);
			joint6DOF.setLimit(4, 0, 0);
			joint6DOF.setLimit(5, 0, 0);
			
			dynamicsWorld.addConstraint(joint6DOF, true);
		}

		clientResetScene();
	}
	
	public static void main(String[] args) throws LWJGLException {
		
		File JGLLib = null;
		
		switch(LWJGLUtil.getPlatform())
        {
            case LWJGLUtil.PLATFORM_WINDOWS:
            {
                JGLLib = new File("./libs/windows");
            }
            break;

            case LWJGLUtil.PLATFORM_LINUX:
            {
                JGLLib = new File("./libs/linux");
            }
            break;

            case LWJGLUtil.PLATFORM_MACOSX:
            {
                JGLLib = new File("./libs/macosx");
            }
            break;
        }

        System.setProperty("org.lwjgl.librarypath", JGLLib.getAbsolutePath());
		
		
		
		BasicDemo ccdDemo = new BasicDemo(LWJGL.getGL());
		ccdDemo.initPhysics();
		ccdDemo.getDynamicsWorld().setDebugDrawer(new GLDebugDrawer(LWJGL.getGL()));

		LWJGL.main(args, 800, 600, "Bullet Physics Demo", ccdDemo);
	}

	
}
