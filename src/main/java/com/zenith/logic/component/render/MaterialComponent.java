package com.zenith.logic.component.render;

import com.zenith.logic.component.Component;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.GLMaterial;

/**
 * 材质组件
 * 负责管理实体的视觉外观属性，包括贴图、光泽度及 Shader 参数。
 */
public class MaterialComponent extends Component {

    /** 关联的底层材质对象 **/
    private final GLMaterial material;

    /**
     * 初始化材质组件
     * @param material 底层材质实例
     */
    public MaterialComponent(GLMaterial material) {
        this.material = material;
    }

    /**
     * 获取当前挂载的材质
     * @return GLMaterial 实例
     */
    public GLMaterial getMaterial() {
        return material;
    }

    /**
     * 快捷设置材质的主贴图
     * @param texture 纹理对象
     */
    public void setMainTexture(Texture texture) {
        if (material != null) {
            material.setTexture("u_diffuse", texture);
        }
    }

    @Override public void onCreate() {}
    @Override public void onUpdate(float deltaTime) {}
    @Override public void onDestroy() {}
}